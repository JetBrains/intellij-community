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

import com.intellij.openapi.util.io.ByteArraySequence;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class StorageTest extends StorageTestBase {
  public void testSmoke() throws Exception {
    final int record = myStorage.createNewRecord();
    myStorage.writeBytes(record, new ByteArraySequence("Hello".getBytes()), false);
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
      final int record = myStorage.createNewRecord();
      myStorage.writeBytes(record, new ByteArraySequence(hello.getBytes()), true);  // fixed size optimization is mor than 50 percents here!
      records[i] = record;
    }

    for (int record : records) {
      assertEquals(hello, new String(myStorage.readBytes(record)));
    }

    long timedelta = System.currentTimeMillis() - start;
    System.out.println("Done for " + timedelta + "msec.");
  }

  public void testAppender() throws Exception {
    final int r = myStorage.createNewRecord();

    DataOutputStream out = new DataOutputStream(myStorage.appendStream(r));
    for (int i = 0; i < 10000; i++) {
      out.writeInt(i);
      if (i % 100 == 0) {
        myStorage.readStream(r); // Drop the appenders cache
        out.close();
        out = new DataOutputStream(myStorage.appendStream(r));
      }
    }
    
    out.close();


    DataInputStream in = new DataInputStream(myStorage.readStream(r));
    for (int i = 0; i < 10000; i++) {
      assertEquals(i, in.readInt());
    }

    in.close();
  }
  
  public void testAppender2() throws Exception {
    int r = myStorage.createNewRecord();
    appendNBytes(r, 64);
    appendNBytes(r, 256);
    appendNBytes(r, 512);
  }

  protected void appendNBytes(final int r, final int len) throws IOException {
    DataOutputStream out = new DataOutputStream(myStorage.appendStream(r));
    for (int i = 0; i < len; i++) {
      out.write(0);
    }
    myStorage.readBytes(r);
  }
}