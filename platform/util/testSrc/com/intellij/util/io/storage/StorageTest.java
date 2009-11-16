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

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public class StorageTest extends TestCase {
  protected AbstractStorage myStorage;

  protected void setUp() throws Exception {
    super.setUp();
    myStorage = Storage.create(getFileName());
  }

  protected String getFileName() {
    return FileUtil.getTempDirectory() + File.separatorChar + getName();
  }

  protected void tearDown() throws Exception {
    Disposer.dispose(myStorage);
    Storage.deleteFiles(getFileName());
    super.tearDown();
  }

  public void testSmoke() throws Exception {
    int record = myStorage.createNewRecord(0);
    myStorage.writeBytes(record, "Hello".getBytes());
    assertEquals("Hello", new String(myStorage.readBytes(record)));
  }

  public void testStress() throws Exception {
    StringBuffer data = new StringBuffer();
    for (int i = 0; i < 100; i++) {
      data.append("Hello ");
    }
    String hello = data.toString();

    long start = System.currentTimeMillis();
    final int count = 100000;
    int[] records = new int[count];

    for (int i = 0; i < count; i++) {
      byte[] bytes = hello.getBytes();
      int record = myStorage.createNewRecord(bytes.length);
      myStorage.writeBytes(record, bytes);
      records[i] = record;
    }

    for (int record : records) {
      byte[] bytes = myStorage.readBytes(record);
      assertEquals(hello, new String(bytes));
    }

    long timedelta = System.currentTimeMillis() - start;
    System.out.println("Done for " + timedelta + "msec.");
  }

  public void testAppender() throws Exception {
    final int count = 1000;
    int r = myStorage.createNewRecord(count * 4);

    AbstractStorage.AppenderStream out = myStorage.appendStream(r);
    for (int i = 0; i < count; i++) {
      out.writeInt(i);
      if (i % 100 == 0) {
        out.close();
        out = myStorage.appendStream(r);
      }
    }
    
    out.close();

    DataInputStream in = myStorage.readStream(r);
    for (int i = 0; i < count; i++) {
      assertEquals(i, in.readInt());
    }

    in.close();
  }
  
  public void testAppender2() throws Exception {
    int r = myStorage.createNewRecord(0);
    appendNBytes(r, 64);
    appendNBytes(r, 256);
    appendNBytes(r, 512);
  }

  private void appendNBytes(final int r, final int len) throws IOException {
    DataOutputStream out = new DataOutputStream(myStorage.appendStream(r));
    for (int i = 0; i < len; i++) {
      out.write(0);
    }
    myStorage.readBytes(r);
  }
}