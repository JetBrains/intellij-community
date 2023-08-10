// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.ByteArraySequence;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class StorageTest extends StorageTestBase {
  private static final Logger LOG = Logger.getInstance(StorageTest.class);
  @Test
  public void testSmoke() throws Exception {
    final int record = myStorage.createNewRecord();
    myStorage.writeBytes(record, new ByteArraySequence("Hello".getBytes(StandardCharsets.UTF_8)), false);
    assertEquals("Hello", new String(myStorage.readBytes(record), StandardCharsets.UTF_8));
  }

  @Test
  public void testStress() throws Exception {
    String hello = "Hello ".repeat(100);

    long start = System.currentTimeMillis();
    final int count = 100000;
    int[] records = new int[count];

    for (int i = 0; i < count; i++) {
      final int record = myStorage.createNewRecord();
      myStorage.writeBytes(record, new ByteArraySequence(hello.getBytes(StandardCharsets.UTF_8)), true);  // fixed size optimization is mor than 50 percents here!
      records[i] = record;
    }

    for (int record : records) {
      assertEquals(hello, new String(myStorage.readBytes(record), StandardCharsets.UTF_8));
    }

    long timedelta = System.currentTimeMillis() - start;
    LOG.debug("Done in " + timedelta + " ms");
  }

  @Test
  public void testAppender() throws Exception {
    final int r = myStorage.createNewRecord();

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataOutputStream out = new DataOutputStream(myStorage.appendStream(r));
    for (int i = 0; i < 10000; i++) {
      out.writeInt(i);
      if (i % 100 == 0) {
        myStorage.readStream(r); // Drop the appenders cache
        out.close();
        out = new DataOutputStream(myStorage.appendStream(r));
      }
    }
    out.close();

    try (DataInputStream in = new DataInputStream(myStorage.readStream(r))) {
      for (int i = 0; i < 10000; i++) {
        assertEquals(i, in.readInt());
      }
    }
  }

  @Test
  public void testAppender2() throws Exception {
    int r = myStorage.createNewRecord();
    appendNBytes(r, 64);
    appendNBytes(r, 256);
    appendNBytes(r, 512);
  }

  private void appendNBytes(int r, int len) throws IOException {
    try (DataOutputStream out = new DataOutputStream(myStorage.appendStream(r))) {
      for (int i = 0; i < len; i++) {
        out.write(0);
      }
    }
    myStorage.readBytes(r);
  }
}