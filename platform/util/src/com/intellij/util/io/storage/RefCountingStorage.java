/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.PagePool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class RefCountingStorage extends AbstractStorage {
  public RefCountingStorage(String path) throws IOException {
    super(path);
  }

  @Override
  protected byte[] readBytes(int record) throws IOException {
    synchronized (myLock) {
      byte[] result = super.readBytes(record);
      InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(result));
      try {
        return StreamUtil.loadFromStream(in);
      }
      finally {
        in.close();
      }
    }
  }

  @Override
  protected void appendBytes(int record, byte[] bytes) throws IOException {
    throw new IncorrectOperationException("Appending is not supported");
  }

  @Override
  protected void writeBytes(int record, byte[] bytes) throws IOException {
    synchronized (myLock) {
      ByteArrayOutputStream s = new ByteArrayOutputStream();
      DeflaterOutputStream out = new DeflaterOutputStream(s);
      try {
        out.write(bytes);
      }
      finally {
        out.close();
      }
      super.writeBytes(record, s.toByteArray());
    }
  }

  @Override
  protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
    return new RefCountingRecordsTable(recordsFile, pool);
  }

  public int acquireNewRecord() throws IOException {
    synchronized (myLock) {
      int record = myRecordsTable.createNewRecord();
      ((RefCountingRecordsTable)myRecordsTable).incRefCount(record);
      return record;
    }
  }

  public void acquireRecord(int record) {
    synchronized (myLock) {
      ((RefCountingRecordsTable)myRecordsTable).incRefCount(record);
    }
  }

  public void releaseRecord(int record) throws IOException {
    synchronized (myLock) {
      if (((RefCountingRecordsTable)myRecordsTable).decRefCount(record)) {
        doDeleteRecord(record);
      }
    }
  }

  public int getRefCount(int record) {
    synchronized (myLock) {
      return ((RefCountingRecordsTable)myRecordsTable).getRefCount(record);
    }
  }
}
