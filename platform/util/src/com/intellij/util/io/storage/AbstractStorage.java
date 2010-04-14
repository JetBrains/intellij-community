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
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.Forceable;
import com.intellij.util.io.RecordDataOutput;

import java.io.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class AbstractStorage implements Forceable, Disposable {

  public abstract int createNewRecord(int requiredLength) throws IOException;

  public abstract void deleteRecord(int record);

  public StorageDataOutput writeStream(final int record) {
    return new StorageDataOutput(record);
  }

  public AppenderStream appendStream(int record) {
    return new AppenderStream(record);
  }

  public DataInputStream readStream(int record) {
    final byte[] bytes = readBytes(record);
    return new DataInputStream(new ByteArrayInputStream(bytes));
  }

  public abstract byte[] readBytes(int record);

  public abstract void checkSanity(int record);

  public abstract boolean flushSome();

  public abstract int getVersion();

  public abstract void setVersion(int version);

  protected abstract int appendBytes(int record, byte[] bytes);

  public abstract void writeBytes(int record, byte[] bytes);

  public abstract int ensureCapacity(int attAddress, int capacity);

  public class StorageDataOutput extends DataOutputStream implements RecordDataOutput {
    private final int myRecordId;

    public StorageDataOutput(int recordId) {
      super(new ByteArrayOutputStream());
      myRecordId = recordId;
    }

    public void close() throws IOException {
      super.close();
      AbstractStorage.this.writeBytes(myRecordId, ((ByteArrayOutputStream)out).toByteArray());
    }

    public int getRecordId() {
      return myRecordId;
    }
  }

  public class AppenderStream extends DataOutputStream {
    private int myRecordId;

    public AppenderStream(int recordId) {
      super(new ByteArrayOutputStream());
      myRecordId = recordId;
    }

    public void close() throws IOException {
      super.close();
      myRecordId = appendBytes(myRecordId, ((ByteArrayOutputStream)out).toByteArray());
    }

    public int getRecordId() {
      return myRecordId;
    }
  }
}
