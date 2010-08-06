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
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.RandomAccessDataFile;

import java.io.File;
import java.io.IOException;

class DataTable implements Disposable, Forceable {
  private static final int HEADER_SIZE = 32;
  private static final int CONNECTED_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f;

  private final RandomAccessDataFile myFile;
  private volatile int myWasteSize;

  private static final int HEADER_MAGIC_OFFSET = 0;
  private static final int HEADER_WASTE_SIZE_OFFSET = 4;
  private boolean myIsDirty = false;

  public DataTable(final File filePath, final PagePool pool) throws IOException {
    myFile = new RandomAccessDataFile(filePath, pool);
    if (myFile.length() == 0) {
      fillInHeader(CONNECTED_MAGIC, 0);
      myIsDirty = true;
    }
    else {
      readInHeader(filePath);
    }
  }

  public boolean isCompactNecessary() {
    return ((double)myWasteSize)/myFile.length() > 0.25 && myWasteSize > 3 * FileUtil.MEGABYTE;
  }

  private void readInHeader(File filePath) throws IOException {
    int magic = myFile.getInt(HEADER_MAGIC_OFFSET);
    if (magic != SAFELY_CLOSED_MAGIC) {
      myFile.dispose();
      throw new IOException("Records table for '" + filePath + "' haven't been closed correctly. Rebuild required.");
    }
    myWasteSize = myFile.getInt(HEADER_WASTE_SIZE_OFFSET);
  }

  private void fillInHeader(int magic, int wasteSize) {
    myFile.putInt(HEADER_MAGIC_OFFSET, magic);
    myFile.putInt(HEADER_WASTE_SIZE_OFFSET, wasteSize);
  }

  public void readBytes(long address, byte[] bytes) {
    myFile.get(address, bytes, 0, bytes.length);
  }

  public void writeBytes(long address, byte[] bytes) {
    markDirty();
    myFile.put(address, bytes, 0, bytes.length);
  }

  public long allocateSpace(int len) {
    final long result = Math.max(myFile.length(), HEADER_SIZE);

    // Fill them in so we won't give out wrong address from allocateSpace() next time if they still not finished writing to allocated page
    writeBytes(result + len - 1, new byte[]{0});
    return result;
  }

  public void reclaimSpace(int len) {
    myWasteSize += len;
  }

  public void dispose() {
    if (!myFile.isDisposed()) {
      markClean();
      myFile.dispose();
    }
  }

  public void force() {
    markClean();
    myFile.force();
  }

  public boolean flushSome(int maxPages) {
    myFile.flushSomePages(maxPages);
    if (!myFile.isDirty()) {
      force();
      return true;
    }
    return false;
  }

  public boolean isDirty() {
    return myIsDirty || myFile.isDirty();
  }

  private void markClean() {
    if (myIsDirty) {
      myIsDirty = false;
      fillInHeader(SAFELY_CLOSED_MAGIC, myWasteSize);
    }
  }

  private void markDirty() {
    if (!myIsDirty) {
      myIsDirty = true;
      fillInHeader(CONNECTED_MAGIC, 0);
    }
  }

  public int getWaste() {
    return myWasteSize;
  }

  public long getFileSize() {
    return myFile.length();
  }
}