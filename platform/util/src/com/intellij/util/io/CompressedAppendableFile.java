// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CompressionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.SLRUMap;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Arrays;

/**
 * Random read append only file, that internally consists of sequence of (compressed) chunks with the same length (buffer size) +
 * tail that is smaller that buffer size. Main file contains compressed data chunks, there are also chunk length table (.s) and incomplete
 * chunk file (.at).
 * (Decompressed) chunks are cached.
 */
public class CompressedAppendableFile {
  private final File myBaseFile;
  private final LowMemoryWatcher myLowMemoryWatcher;

  // force will clear the buffer and reset the position
  private byte[] myNextChunkBuffer;
  private int myBufferPosition;
  private boolean myDirty;

  private short[] myChunkLengthTable;
  private int myChunkTableLength;
  private static final int FACTOR = 32;
  private long [] myChunkOffsetTable; // one long offset per FACTOR compressed chunks
  private static final boolean doDebug = SystemProperties.getBooleanProperty("idea.compressed.file.self.check", false);
  private final TLongArrayList myCompressedChunksFileOffsets = doDebug ? new TLongArrayList() : null;

  public static final int PAGE_LENGTH = SystemProperties.getIntProperty("idea.compressed.file.page.length", 32768);
  private static final int MAX_PAGE_LENGTH = 0xFFFF;

  private long myFileLength;
  private long myUncompressedFileLength = -1;

  private final int myAppendBufferLength;
  private static final int myMinAppendBufferLength = 1024;

  static final String INCOMPLETE_CHUNK_LENGTH_FILE_EXTENSION = ".s";

  private static int ourFilesCount;
  private final int myCount = ourFilesCount++;

  public CompressedAppendableFile(File file) {
    this(file, 32768);
  }

  private CompressedAppendableFile(File file, int bufferSize) {
    myBaseFile = file;
    myAppendBufferLength = bufferSize;
    assert bufferSize <= MAX_PAGE_LENGTH; // length of compressed buffer size should be in short range
    file.getParentFile().mkdirs();

    myLowMemoryWatcher = LowMemoryWatcher.register(() -> {
      dropCaches();

      synchronized (ourDecompressedCache) {
        ourDecompressedCache.clear();
      }
    });
  }

  public synchronized <Data> Data read(final long addr, KeyDescriptor<Data> descriptor) throws IOException {
    try (DataInputStream stream = getStream(addr)) {
      return descriptor.read(stream);
    }
  }

  @NotNull
  public synchronized DataInputStream getStream(final long addr) throws IOException {
    initChunkLengthTable();
    loadAppendBuffer();
    return new DataInputStream(
      new SegmentedChunkInputStream(addr, myChunkTableLength, myNextChunkBuffer, myBufferPosition)
    );
  }

  protected File getChunkLengthFile() {
    return new File(myBaseFile.getPath() + INCOMPLETE_CHUNK_LENGTH_FILE_EXTENSION);
  }

  private synchronized void initChunkLengthTable() throws IOException {
    if (myChunkLengthTable != null) return;
    File chunkLengthFile = getChunkLengthFile();

    if (chunkLengthFile.exists()) {
      try (DataInputStream chunkLengthStream = new DataInputStream(new BufferedInputStream(
        new LimitedInputStream(new FileInputStream(chunkLengthFile), (int)chunkLengthFile.length()) {
          @Override
          public int available() {
            return remainingLimit();
          }
        }, 32768))) {
        short[] chunkLengthTable = new short[(int)(chunkLengthFile.length() / 2)];
        int chunkLengthTableLength = 0;

        long o = 0;
        while (chunkLengthStream.available() != 0) {
          int chunkLength = DataInputOutputUtil.readINT(chunkLengthStream);
          o += chunkLength;
          if (chunkLengthTableLength == chunkLengthTable.length) {
            chunkLengthTable = reallocShortTable(chunkLengthTable);
          }
          chunkLengthTable[chunkLengthTableLength++] = (short)chunkLength;
          if (doDebug) myCompressedChunksFileOffsets.add(o);
        }
        myChunkLengthTable = chunkLengthTable;
        myChunkTableLength = chunkLengthTableLength;

        if (myChunkTableLength >= FACTOR) {
          long[] chunkOffsetTable = new long[myChunkTableLength / FACTOR];
          long offset = 0;
          for (int i = 0; i < chunkOffsetTable.length; ++i) {
            int start = i * FACTOR;
            for (int j = 0; j < FACTOR; ++j) {
              offset += chunkLengthTable[start + j] & MAX_PAGE_LENGTH;
            }
            chunkOffsetTable[i] = offset;
          }
          myChunkOffsetTable = chunkOffsetTable;
          if (doDebug) { // check all offsets
            for (int i = 0; i < chunkLengthTableLength; ++i) {
              calcOffsetOfPage(i);
            }
          }
        }
        else {
          myChunkOffsetTable = ArrayUtil.EMPTY_LONG_ARRAY;
        }

        myFileLength = calcOffsetOfPage(myChunkTableLength - 1);
      }
    }
    else {
      myChunkLengthTable = ArrayUtilRt.EMPTY_SHORT_ARRAY;
      myChunkTableLength = 0;
      myChunkOffsetTable = ArrayUtil.EMPTY_LONG_ARRAY;
      myFileLength = 0;
    }

    if (myUncompressedFileLength == -1) {
      long tempFileLength = getIncompleteChunkFile().length();
      myUncompressedFileLength = ((long)myChunkTableLength * myAppendBufferLength) + tempFileLength;
      if (myUncompressedFileLength != myFileLength + tempFileLength) {
        if (CompressionUtil.DUMP_COMPRESSION_STATS) {
          System.out.println(myUncompressedFileLength + "->" + (myFileLength + tempFileLength) + " for " + myBaseFile);
        }
      }
    }
  }

  private static final FileChunkReadCache ourDecompressedCache = new FileChunkReadCache();

  @NotNull
  private synchronized byte[] loadChunk(int chunkNumber) throws IOException {
    try {
      if (myChunkLengthTable == null) initChunkLengthTable();
      assert chunkNumber < myChunkTableLength;

      try (DataInputStream keysStream = getChunkStream(getChunksFile(), chunkNumber)) {
        if (keysStream.available() > 0) {
          byte[] decompressedBytes = decompress(keysStream);
          if (decompressedBytes.length != myAppendBufferLength) {
            assert false;
          }
          return decompressedBytes;
        }
      }

      assert false:"data corruption detected:"+chunkNumber + "," + myChunkTableLength;
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
    catch (RuntimeException | AssertionError e) { // CorruptedException, ArrayIndexOutofBounds, etc
      throw new IOException(e);
    }
  }

  @NotNull
  private DataInputStream getChunkStream(final File appendFile, int pageNumber) throws IOException {
    assert myFileLength != 0;
    int limit;
    long pageStartOffset;
    final long pageEndOffset = pageNumber < myChunkTableLength ? calcOffsetOfPage(pageNumber) : myFileLength;

    if (pageNumber > 0) {
      pageStartOffset = calcOffsetOfPage(pageNumber - 1);
      limit = (int)(pageEndOffset - pageStartOffset);
    } else {
      pageStartOffset = 0;
      limit = (int)pageEndOffset;
    }

    return new DataInputStream(getChunkInputStream(appendFile, pageStartOffset, limit));
  }

  private long calcOffsetOfPage(int pageNumber) {
    final int calculatedOffset = (pageNumber + 1) / FACTOR;
    long offset = calculatedOffset > 0 ? myChunkOffsetTable[calculatedOffset - 1]:0;
    final int baseOffset = calculatedOffset * FACTOR;
    for(int index = 0, len = (pageNumber + 1) % FACTOR; index < len; ++index) {
      offset += myChunkLengthTable[baseOffset + index] & MAX_PAGE_LENGTH;
    }
    if (doDebug) {
      assert myCompressedChunksFileOffsets.get(pageNumber) == offset;
    }
    return offset;
  }

  @NotNull
  protected InputStream getChunkInputStream(File appendFile, long offset, int pageSize) throws IOException {
    FileInputStream in = new FileInputStream(appendFile);
    if (offset > 0) {
      in.skip(offset);
    }
    return new BufferedInputStream(new LimitedInputStream(in, pageSize) {
      @Override
      public int available() {
        return remainingLimit();
      }
    }, pageSize);
  }

  public synchronized <Data> void append(Data value, KeyDescriptor<Data> descriptor) throws IOException {
    final BufferExposingByteArrayOutputStream bos = new BufferExposingByteArrayOutputStream();
    DataOutput out = new DataOutputStream(bos);
    descriptor.save(out, value);
    final int size = bos.size();
    final byte[] buffer = bos.getInternalBuffer();
    append(buffer, size);
  }

  public void append(byte[] buffer, int size) throws IOException {
    append(buffer, 0, size);
  }

  public synchronized void append(byte[] buffer, int offset, int size) throws IOException {
    if (size == 0) return;

    if (myNextChunkBuffer == null) loadAppendBuffer();
    if (myNextChunkBuffer.length != myAppendBufferLength && myBufferPosition + size >= myNextChunkBuffer.length) {
      int newBufferSize = calcBufferSize(myBufferPosition + size);
      if (newBufferSize != myNextChunkBuffer.length) {
        myNextChunkBuffer = Arrays.copyOf(myNextChunkBuffer, newBufferSize);
      }
    }

    int bufferPosition = offset;
    int sizeToWrite = size;

    while (sizeToWrite > 0) {
      int bytesToWriteInTheBuffer = Math.min(myNextChunkBuffer.length - myBufferPosition, sizeToWrite);
      System.arraycopy(buffer, bufferPosition, myNextChunkBuffer, myBufferPosition, bytesToWriteInTheBuffer);
      myBufferPosition += bytesToWriteInTheBuffer;
      bufferPosition += bytesToWriteInTheBuffer;
      sizeToWrite -= bytesToWriteInTheBuffer;
      saveNextChunkIfNeeded();
    }

    if (myUncompressedFileLength == -1) length();
    myUncompressedFileLength += size;
    myDirty = true;
  }

  private synchronized void loadAppendBuffer() throws IOException {
    if (myNextChunkBuffer != null) return;

    File tempAppendFile = getIncompleteChunkFile();
    if (tempAppendFile.exists()) {
      myBufferPosition = (int)tempAppendFile.length();
      myNextChunkBuffer = new byte[calcBufferSize(myBufferPosition)];

      try (FileInputStream stream = new FileInputStream(tempAppendFile)) {
        stream.read(myNextChunkBuffer, 0, myBufferPosition);
      }
    }
    else {
      myBufferPosition = 0;
      myNextChunkBuffer = new byte[myMinAppendBufferLength];
    }
  }

  private int calcBufferSize(int position) {
    return Math.min(
      myAppendBufferLength,
      Integer.highestOneBit(Math.max(myMinAppendBufferLength - 1, position)) << 1
    );
  }

  private void saveNextChunkIfNeeded() throws IOException {
    if (myBufferPosition == myNextChunkBuffer.length) {
      BufferExposingByteArrayOutputStream compressedOut = new BufferExposingByteArrayOutputStream();
      DataOutputStream compressedDataOut = new DataOutputStream(compressedOut);
      compress(compressedDataOut, myNextChunkBuffer);
      compressedDataOut.close();

      assert compressedDataOut.size() <= MAX_PAGE_LENGTH; // we need to be in short range for chunk length table
      saveChunk(compressedOut, myFileLength);

      myBufferPosition = 0;
      initChunkLengthTable();

      myFileLength += compressedOut.size();
      if (doDebug) myCompressedChunksFileOffsets.add(myFileLength);

      if (myChunkLengthTable.length == myChunkTableLength) {
        myChunkLengthTable = reallocShortTable(myChunkLengthTable);
      }

      myChunkLengthTable[myChunkTableLength++] = (short)compressedOut.size();
      if (myChunkTableLength / FACTOR > myChunkOffsetTable.length) {
        long[] newChunkOffsetTable = new long[myChunkOffsetTable.length + 1];
        System.arraycopy(myChunkOffsetTable, 0, newChunkOffsetTable, 0, myChunkOffsetTable.length);
        newChunkOffsetTable[myChunkOffsetTable.length] = myFileLength;
        myChunkOffsetTable = newChunkOffsetTable;
      }

      byte[] bytes = new byte[myAppendBufferLength];
      System.arraycopy(myNextChunkBuffer, 0, bytes, 0, myAppendBufferLength);
      ourDecompressedCache.put(this, myChunkTableLength - 1, bytes);
    }
  }

  @NotNull
  private static short[] reallocShortTable(short[] table) {
    return ArrayUtil.realloc(table, Math.max(table.length * 8 / 5, table.length + 1));
  }

  protected int compress(DataOutputStream compressedDataOut, byte[] buffer) throws IOException {
    return CompressionUtil.writeCompressedWithoutOriginalBufferLength(compressedDataOut, buffer, myAppendBufferLength);
  }

  @NotNull
  protected byte[] decompress(DataInputStream keysStream) throws IOException {
    return CompressionUtil.readCompressedWithoutOriginalBufferLength(keysStream, myAppendBufferLength);
  }

  protected void saveChunk(BufferExposingByteArrayOutputStream compressedChunk, long endOfFileOffset) throws IOException {
    try (DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(getChunksFile(), true)))) {
      stream.write(compressedChunk.getInternalBuffer(), 0, compressedChunk.size());
    }

    try (DataOutputStream chunkLengthStream = new DataOutputStream(
      new BufferedOutputStream(new FileOutputStream(getChunkLengthFile(), true)))) {
      DataInputOutputUtil.writeINT(chunkLengthStream, compressedChunk.size());
    }
  }

  @NotNull
  protected File getChunksFile() {
    return new File(myBaseFile.getPath() + ".a");
  }

  private void saveIncompleteChunk() {
    if (myNextChunkBuffer != null && myDirty) {
      File incompleteChunkFile = getIncompleteChunkFile();

      try {
        saveNextChunkIfNeeded();
        if (myBufferPosition != 0) {
          try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(incompleteChunkFile))) {
            stream.write(myNextChunkBuffer, 0, myBufferPosition);
          }
        } else {
          incompleteChunkFile.delete();
        }
      } catch (FileNotFoundException ex) {
        File parentFile = incompleteChunkFile.getParentFile();
        if (!parentFile.exists()) {
          if(parentFile.mkdirs()) {
            saveIncompleteChunk();
            return;
          } else {
            throw new RuntimeException("Failed to write:"+incompleteChunkFile, ex);
          }
        }
        throw new RuntimeException(ex);
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
      myDirty = false;
    }
  }

  @NotNull
  private File getIncompleteChunkFile() {
    return new File(myBaseFile.getPath() + ".at");
  }

  public synchronized void dropCaches() {
    // TODO:
    //force();
    //myChunkLengthTable = null;
    //myChunkTableLength = 0;
    //myChunkOffsetTable = null;
    //myNextChunkBuffer = null;
    //myBufferPosition = 0;
    //if (doDebug) myCompressedChunksFileOffsets.clear();
  }

  public synchronized void force() {
    saveIncompleteChunk();
  }

  public synchronized void dispose() {
    force();
    myLowMemoryWatcher.stop();
  }

  public synchronized long length() {
    if (myUncompressedFileLength == -1) {
      if (myChunkLengthTable == null) {
        try {
          initChunkLengthTable();
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
    return myUncompressedFileLength;
  }

  public synchronized boolean isDirty() {
    return myDirty;
  }

  private static class FileChunkReadCache extends SLRUMap<FileChunkKey<CompressedAppendableFile>, byte[]> {
    private final FileChunkKey<CompressedAppendableFile> myKey = new FileChunkKey<>(null, 0);

    FileChunkReadCache() {
      super(64, 64);
    }

    @NotNull
    public byte[] get(CompressedAppendableFile file, int page) throws IOException {
      byte[] bytes;
      synchronized (this) {
        myKey.setup(file, page);
        bytes = get(myKey);
        if (bytes != null) return bytes;
      }

      bytes = file.loadChunk(page);   // out of lock
      synchronized (this) {
        put(file, page, bytes);
      }
      return bytes;
    }

    public void put(CompressedAppendableFile file, long page, byte[] bytes) {
      synchronized (this) {
        myKey.setup(file, page);
        put(myKey, bytes);
      }
    }
  }

  private class SegmentedChunkInputStream extends InputStream {
    private final long myAddr;
    private final int myChunkLengthTableSnapshotLength;
    private final byte[] myNextChunkBufferSnapshot;
    private final int myBufferPositionSnapshot;

    private InputStream bytesFromCompressedBlock;
    private InputStream bytesFromTempAppendBlock;

    private int myCurrentPageNumber;
    private int myPageOffset;

    SegmentedChunkInputStream(long addr, int chunkLengthTableSnapshotLength, byte[] tableRef, int position) {
      myAddr = addr;
      myChunkLengthTableSnapshotLength = chunkLengthTableSnapshotLength;
      myNextChunkBufferSnapshot = tableRef;
      myBufferPositionSnapshot = position;
      myCurrentPageNumber = (int)(myAddr / myAppendBufferLength);
      myPageOffset = (int)(myAddr % myAppendBufferLength);
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
      if (bytesFromCompressedBlock == null) {
        byte[] decompressedBytes = myCurrentPageNumber < myChunkLengthTableSnapshotLength ?
                                   ourDecompressedCache.get(CompressedAppendableFile.this, myCurrentPageNumber) : ArrayUtilRt.EMPTY_BYTE_ARRAY;
        bytesFromCompressedBlock = new ByteArrayInputStream(decompressedBytes, myPageOffset, decompressedBytes.length);
      }
      int readBytesCount = 0;

      if (bytesFromCompressedBlock.available() > 0) {
        readBytesCount = bytesFromCompressedBlock.read(b, off, len);
        myPageOffset += readBytesCount;
        if (myPageOffset == myAppendBufferLength) {
          ++myCurrentPageNumber;
          myPageOffset = 0;
        }

        if (readBytesCount == len) return readBytesCount;
      }

      while (myCurrentPageNumber < myChunkLengthTableSnapshotLength) {
        byte[] decompressedBytes = ourDecompressedCache.get(CompressedAppendableFile.this, myCurrentPageNumber);
        bytesFromCompressedBlock = new ByteArrayInputStream(decompressedBytes, 0, decompressedBytes.length);
        int read = bytesFromCompressedBlock.read(b, off + readBytesCount, len - readBytesCount);
        myPageOffset += read;
        if (myPageOffset == myAppendBufferLength) {
          ++myCurrentPageNumber;
          myPageOffset = 0;
        }
        readBytesCount += read;
        if (readBytesCount == len) return readBytesCount;
      }

      if (bytesFromTempAppendBlock == null) {
        bytesFromTempAppendBlock = new ByteArrayInputStream(myNextChunkBufferSnapshot, myPageOffset, myBufferPositionSnapshot);
      }
      return readBytesCount + bytesFromTempAppendBlock.read(b, off + readBytesCount, len - readBytesCount);
    }

    @Override
    public int read() throws IOException {
      byte[] buf = {0};
      int read = read(buf);
      if (read == -1) return -1;
      return buf[0] & 0xFF;
    }
  }

  @Override
  public int hashCode() {
    return myCount;
  }
}