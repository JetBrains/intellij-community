/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.util.io;

import java.io.*;

public class PersistentHashMapValueStorage {
  private final DataOutputStream myAppender;
  private RAReader myReader;
  private long mySize;
  private final File myFile;
  private boolean myCompactionMode = false;

  public PersistentHashMapValueStorage(String path) throws IOException {
    myAppender = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path, true)));
    myFile = new File(path);
    myReader = new FileReader(myFile);
    mySize = myFile.length();

    if (mySize == 0) {
      appendBytes("Header Record For PersistentHashMapValuStorage".getBytes(), 0);
    }
  }

  public long appendBytes(byte[] data, long prevChunkAddress) throws IOException {
    assert !myCompactionMode;
    long result = mySize;
    myAppender.writeLong(prevChunkAddress);
    myAppender.writeInt(data.length);
    myAppender.write(data);
    mySize += data.length + 8 + 4;

    return result;
  }

  /**
   * Reads bytes pointed by tailChunkAddress into result passed, returns new address if linked list compactification have been performed
   */
  public long readBytes(long tailChunkAddress, byte[] result) throws IOException {
    int size = result.length;
    if (size == 0) return tailChunkAddress;

    force();

    int bytesRead = 0;
    long chunk = tailChunkAddress;
    int chunkCount = 0;

    byte[] headerBits = new byte[8 + 4];
    while (chunk != 0) {
      myReader.get(chunk, headerBits, 0, 12);
      final long prevChunkAddress = Bits.getLong(headerBits, 0);
      final int chunkSize = Bits.getInt(headerBits, 8);
      final int off = size - bytesRead - chunkSize;
      
      checkPreconditions(result, chunkSize, off);
      
      myReader.get(chunk + 12, result, off, chunkSize);
      chunk = prevChunkAddress;
      bytesRead += chunkSize;
      chunkCount++;
    }
    
    //assert bytesRead == size;
    if (bytesRead != size) {
      throw new IOException("Read from storage " + bytesRead + " bytes, but requested " + size + " bytes");
    }
    
    if (chunkCount > 1 && !myCompactionMode) {
      return appendBytes(result, 0);
    }

    return tailChunkAddress;
  }

  private static void checkPreconditions(final byte[] result, final int chunkSize, final int off) throws IOException {
    if (chunkSize < 0) {
      throw new IOException("Value storage corrupted: negative chunk size");
    }
    if (off < 0) {
      throw new IOException("Value storage corrupted: negative offset");
    }
    if (chunkSize > result.length - off) {
      throw new IOException("Value storage corrupted");
    }
  }

  public void force() {
    try {
      myAppender.flush();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void dispose() {
    try {
      myAppender.close();
      myReader.dispose();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void switchToCompactionMode(PagedFileStorage.StorageLock lock) {
    myReader.dispose();
    try {
      myReader = new MappedReader(myFile, lock);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    myCompactionMode = true;
  }

  public static PersistentHashMapValueStorage create(final String path) throws IOException {
    return new PersistentHashMapValueStorage(path);
  }

  private interface RAReader {
    void get(long addr, byte[] dst, int off, int len) throws IOException;
    void dispose();
  }

  private static class MappedReader implements RAReader {
    private final PagedFileStorage myHolder;

    private MappedReader(File file, PagedFileStorage.StorageLock lock) throws IOException {
      myHolder = new PagedFileStorage(file, lock);
      myHolder.length();
    }

    public void get(final long addr, final byte[] dst, final int off, final int len) {
      myHolder.get((int)addr, dst, off, len);
    }

    public void dispose() {
      myHolder.close();
    }
  }

  private static class FileReader implements RAReader {
    private final RandomAccessFile myFile;

    private FileReader(File file) {
      try {
        myFile = new RandomAccessFile(file, "r");
      }
      catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    public void get(final long addr, final byte[] dst, final int off, final int len) throws IOException {
      myFile.seek(addr);
      myFile.read(dst, off, len);
    }

    public void dispose() {
      try {
        myFile.close();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
