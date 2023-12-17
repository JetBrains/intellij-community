// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSContentAccessor;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogFactory;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogOverMMappedFile;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleHashMap;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.*;
import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog;
import com.intellij.util.io.storage.RecordIdIterator;
import com.intellij.util.io.storage.VFSContentStorage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory.NotClosedProperlyAction.DROP_AND_CREATE_EMPTY_MAP;

/**
 * {@link VFSContentStorage} implemented with memory-mapped files: uses {@link AppendOnlyLogOverMMappedFile} for
 * storing data records (contentHash, content), and uses {@link ExtendibleHashMap} for mapping (contentHash->contentId)
 */
public class VFSContentStorageOverMMappedFile implements VFSContentStorage, Unmappable {
  private static final Logger LOG = Logger.getInstance(VFSContentStorageOverMMappedFile.class);

  private static final int STORAGE_FORMAT_VERSION = 1;

  private static final int EXTERNAL_VERSION_FIELD_NO = 0;

  private static final int CONTENT_HASH_LENGTH = ContentHashEnumerator.SIGNATURE_LENGTH;


  //TODO/MAYBE RC:
  //           1. Check multithreading semantics: it seems like hashToContentRecordIdMap being lock-protected, and
  //              contentStorage being non-blocking give us thread-safety -- but needs to check more carefully


  private final Path storagePath;

  /**
   * Map [hashCodeOf(contentCryptoHash) -> recordId]
   * Beware: recordId is external id, not the actual {@link #contentStorage} id, use {@link #contentIdToStorageId(int)}
   * to convert
   */
  private final ExtendibleHashMap hashToContentRecordIdMap;

  private final AppendOnlyLogOverMMappedFile contentStorage;

  /**
   * Compress (use jdk {@link java.util.zip}) content if > compressContentLargerThan.
   * There is usually no reason to compress small content, but large content compression could
   * win a lot in both disk/memory space, and IO time.
   */
  private final int compressContentLargerThan;

  public VFSContentStorageOverMMappedFile(Path storagePath,
                                          int compressContentLargerThan) throws IOException {
    this.storagePath = storagePath;
    this.compressContentLargerThan = compressContentLargerThan;

    //Use larger pages: content storage is usually quite big.
    int pageSize = 64 * IOUtil.MiB;
    if (pageSize <= FileUtilRt.LARGE_FOR_CONTENT_LOADING) {
      //pageSize is an upper limit on record size for AppendOnlyLogOverMMappedFile:
      throw new IllegalStateException(
        "PageSize(=" + pageSize + ") must be > FileUtilRt.LARGE_FOR_CONTENT_LOADING(=" + FileUtilRt.LARGE_FOR_CONTENT_LOADING + "b), " +
        "otherwise large content can't fit"
      );
    }

    contentStorage = AppendOnlyLogFactory.withDefaults()
      .pageSize(pageSize)
      .failFileIfIncompatible()
      .failIfDataFormatVersionNotMatch(STORAGE_FORMAT_VERSION)
      .open(storagePath);

    Path mapPath = storagePath.resolveSibling(storagePath.getFileName().toString() + ".hashToId");
    if (contentStorage.isEmpty()) {
      //ensure map is also empty
      FileUtil.delete(mapPath);
    }

    hashToContentRecordIdMap = ExtendibleMapFactory.defaults()
      .ifNotClosedProperly(DROP_AND_CREATE_EMPTY_MAP)
      .cleanIfFileIncompatible()
      .open(mapPath);

    if (hashToContentRecordIdMap.isEmpty() && !contentStorage.isEmpty()) {
      LOG.warn("Content map[" + mapPath + "] is empty while content storage is not: re-building map from the storage");
      rebuildMap(contentStorage, hashToContentRecordIdMap);
    }
  }


  @Override
  public int getVersion() throws IOException {
    return contentStorage.getUserDefinedHeaderField(EXTERNAL_VERSION_FIELD_NO);
  }

  @Override
  public void setVersion(int expectedVersion) throws IOException {
    contentStorage.setUserDefinedHeaderField(EXTERNAL_VERSION_FIELD_NO, expectedVersion);
  }

  @Override
  public int storeRecord(@NotNull ByteArraySequence bytes) throws IOException {
    byte[] cryptoHash = PersistentFSContentAccessor.calculateHash(bytes);
    int hash = hashCodeOf(cryptoHash);
    ByteBuffer cryptoHashWrapped = ByteBuffer.wrap(cryptoHash);
    return hashToContentRecordIdMap.lookupOrInsert(
      hash,
      recordId -> {
        long storageId = contentIdToStorageId(recordId);
        Boolean recordHasSameCryptoHash = contentStorage.read(storageId, buffer -> {
          ByteBuffer cryptoHashSlice = buffer.slice(0, CONTENT_HASH_LENGTH);
          return cryptoHashSlice.equals(cryptoHashWrapped);
        });
        return recordHasSameCryptoHash;
      },
      _hash -> {
        ByteArraySequence bytesToStore;
        int uncompressedSize;
        if (shouldCompress(bytes)) {
          bytesToStore = compress(bytes);
          uncompressedSize = -bytes.length();//sign bit indicates 'compressed data'
        }
        else {
          bytesToStore = bytes;
          uncompressedSize = bytes.length();
        }

        //record: cryptoHash[CONTENT_HASH_LENGTH], uncompressedSize(int32), contentBytes[...]
        //  uncompressedSize >= 0 => not compressed data, size=uncompressedSize
        //  uncompressedSize < 0  => compressed data, uncompressed size = -uncompressedSize
        int totalSize = cryptoHash.length + Integer.BYTES + bytesToStore.length();

        long storageId = contentStorage.append(
          buffer -> buffer.put(cryptoHash)
            .putInt(uncompressedSize)
            .put(bytesToStore.getInternalBuffer(), bytesToStore.getOffset(), bytesToStore.length()),
          totalSize
        );
        return storageIdToContentId(storageId);
      }
    );
  }

  private boolean shouldCompress(@NotNull ByteArraySequence contentBytes) {
    return contentBytes.length() > compressContentLargerThan;
  }

  @Override
  public void checkRecord(int recordId,
                          boolean fastCheck) throws IOException {
    long storageId = contentIdToStorageId(recordId);
    contentStorage.read(storageId, buffer -> {
      int recordSize = buffer.limit();
      int recordHeaderSize = CONTENT_HASH_LENGTH + Integer.BYTES;

      if (recordSize < recordHeaderSize) {
        throw new CorruptedException(
          "record[" + recordId + "].length(" + recordSize + "b) < headerSize(" + recordHeaderSize + "b) => record is corrupted");
      }
      int uncompressedSize = buffer.getInt(CONTENT_HASH_LENGTH);

      if (!fastCheck) {
        //check content bytes also: recalculate crypto-hash and compare with the stored one
        byte[] cryptoHashStored = new byte[CONTENT_HASH_LENGTH];
        buffer.get(0, cryptoHashStored);

        buffer.position(CONTENT_HASH_LENGTH + Integer.BYTES);
        byte[] contentBytes;
        if (uncompressedSize >= 0) {
          int contentSize = recordSize - recordHeaderSize;
          contentBytes = new byte[contentSize];
          buffer.get(contentBytes);
        }
        else {
          int actualUncompressedSize = -uncompressedSize;
          contentBytes = decompress(buffer, actualUncompressedSize);
        }

        byte[] cryptoHashCalculated = PersistentFSContentAccessor.calculateHash(contentBytes, 0, contentBytes.length);


        if (!Arrays.equals(cryptoHashStored, cryptoHashCalculated)) {
          throw new CorruptedException("record[" + recordId + "].cryptoHash does not match => record is corrupted\n" +
                                       "\t    stored hash: " + IOUtil.toHexString(cryptoHashStored) + "\n" +
                                       "\tcalculated hash: " + IOUtil.toHexString(cryptoHashCalculated) + "\n"
          );
        }
      }

      return null;
    });
  }

  @Override
  public byte[] contentHash(int recordId) throws IOException {
    long storageId = contentIdToStorageId(recordId);
    return contentStorage.read(storageId, buffer -> {
      byte[] cryptoHash = new byte[CONTENT_HASH_LENGTH];
      buffer.get(cryptoHash);
      return cryptoHash;
    });
  }


  @Override
  public InputStream readStream(int recordId) throws IOException {
    long storageId = contentIdToStorageId(recordId);
    byte[] bytes = contentStorage.read(storageId, buffer -> {
      //record: cryptoHash[CONTENT_HASH_LENGTH], contentSize(int32), contentBytes[contentSize]
      buffer.position(CONTENT_HASH_LENGTH);//skip crypto-hash
      int uncompressedSize = buffer.getInt();
      if (uncompressedSize >= 0) { //not compressed data
        int contentSize = buffer.remaining();
        byte[] contentBytes = new byte[contentSize];
        buffer.get(contentBytes);
        return contentBytes;
      }
      else {// [uncompressedSize<0] => compressed data
        int actualUncompressedSize = -uncompressedSize;
        return decompress(buffer, actualUncompressedSize);
      }
    });

    //MAYBE RC: introduce 'VIGILANT' option there we always check crypto-hash of read/decompressed data
    //          against crypto-has stored?
    return new UnsyncByteArrayInputStream(bytes);
  }

  @Override
  public RecordIdIterator createRecordIdIterator() throws IOException {
    //RC: it is memory-inefficient to collect all the ids, but createRecordIdIterator() is used only in a few
    //    not-performance-critical places, so it is, probably, fine.
    //    For the future: it is better to replace it with .forEach()-style iteration method.
    IntList externalIds = new IntArrayList();
    contentStorage.forEachRecord((storageId, buffer) -> {
      externalIds.add(storageIdToContentId(storageId));
      return true;
    });
    IntListIterator iterator = externalIds.iterator();
    return new RecordIdIterator() {
      @Override
      public boolean hasNextId() {
        return iterator.hasNext();
      }

      @Override
      public int nextId() {
        return iterator.nextInt();
      }

      @Override
      public boolean validId() {
        return true;
      }
    };
  }


  @Override
  public int getRecordsCount() throws IOException {
    return hashToContentRecordIdMap.size();
  }

  @Override
  public boolean isEmpty() throws IOException {
    return contentStorage.isEmpty();
  }

  @Override
  public boolean isDirty() {
    //append-only-log implemented over memory-mapped file, so assume it is 'always in sync'
    return hashToContentRecordIdMap.isDirty();
  }

  @Override
  public void force() throws IOException {
    contentStorage.flush();
    hashToContentRecordIdMap.flush();
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Close [" + storagePath + "] fails"),
      hashToContentRecordIdMap::close,
      contentStorage::close
    );
  }

  @Override
  public void closeAndUnsafelyUnmap() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Can't .closeAndUnsafelyUnmap() " + contentStorage + "/" + hashToContentRecordIdMap),
      contentStorage::closeAndUnsafelyUnmap,
      hashToContentRecordIdMap::closeAndUnsafelyUnmap
    );
  }

  @Override
  public void closeAndClean() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("closeAndClean [" + storagePath + "] fails"),
      hashToContentRecordIdMap::closeAndClean,
      contentStorage::closeAndClean
    );
  }

  //===================== implementation infrastructure: ================================

  private static @NotNull ByteArraySequence compress(@NotNull ByteArraySequence bytes) {
    Deflater deflater = new Deflater();
    try {
      deflater.setInput(bytes.getInternalBuffer(), bytes.getOffset(), bytes.length());
      deflater.finish();
      UnsyncByteArrayOutputStream compressedBytesStream = new UnsyncByteArrayOutputStream(bytes.length() / 2);
      byte[] buffer = new byte[1024];
      while (!deflater.finished()) {
        int bytesDeflated = deflater.deflate(buffer);
        compressedBytesStream.write(buffer, 0, bytesDeflated);
      }
      return compressedBytesStream.toByteArraySequence();
    }
    finally {
      deflater.end();
    }
  }

  private static byte[] decompress(@NotNull ByteBuffer buffer,
                                   int uncompressedSize) throws IOException {
    byte[] bufferForDecompression = new byte[uncompressedSize];
    int contentSize = buffer.remaining();

    //MAYBE RC: use thread-local Inflater/Deflater instances?
    //MAYBE RC: use lz4 compression (lz4.kt)? Pure java impl, already utilized in
    //          indexes, less compression ratio, but much faster...
    Inflater inflater = new Inflater();
    try {
      inflater.setInput(buffer);
      int bytesInflated = inflater.inflate(bufferForDecompression);
      if (bytesInflated != bufferForDecompression.length) {
        throw new IOException("Decompressed bytes[" + bytesInflated + "b out of " + contentSize + "b] " +
                              "!= compressed bytes[" + bufferForDecompression.length + "] " +
                              "=> storage is likely corrupted"
        );
      }
      if (!inflater.finished()) {
        throw new IOException("Decompressed bytes[" + bytesInflated + "b out of " + contentSize + "b] " +
                              "but compressed stream is not finished yet " +
                              "=> storage is likely corrupted"
        );
      }
      return bufferForDecompression;
    }
    catch (DataFormatException e) {
      throw new IOException("Decompression [" + contentSize + "b] was failed => storage is likely corrupted", e);
    }
    finally {
      inflater.end();
    }
  }


  private static int storageIdToContentId(long storageId) throws IOException {
    if (((storageId - 1) & 0b11) != 0) {
      //rely on AppendOnlyLogOverMMappedFile impl detail: records are int32-aligned
      throw new AssertionError("Bug: storageId(=" + storageId + ") expected to be int32-aligned");
    }

    long id = ((storageId - 1) >> 2) + 1;
    //return Math.toIntExact(id);
    if ((int)id != id) {
      throw new IOException("Overflow: storageId(=" + storageId + ") >MAX_INT even after /4");
    }
    return (int)id;
  }

  private static long contentIdToStorageId(int recordId) {
    return (((long)recordId - 1) << 2) + 1;
  }

  private static int hashCodeOf(byte[] contentHash) {
    int hashCode = 0; // take first 4 bytes, this should be good enough hash given we reference git revisions with 7-8 hex digits
    for (int i = 0; i < 4; i++) {
      hashCode = (hashCode << 8) + (contentHash[i] & 0xFF);
    }
    return hashCode;
  }

  private static void rebuildMap(@NotNull AppendOnlyLog contentStorage,
                                 @NotNull /*InOut*/ ExtendibleHashMap hashToRecordIdMap) throws IOException {
    contentStorage.forEachRecord((storageId, buffer) -> {
      byte[] cryptoHashStored = new byte[CONTENT_HASH_LENGTH];
      buffer.get(cryptoHashStored);
      int hash = hashCodeOf(cryptoHashStored);
      int contentId = storageIdToContentId(storageId);
      hashToRecordIdMap.put(hash, contentId);
      return true;
    });
  }
}
