// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.util.io.DataInputOutputUtil;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;

public class DataInputOutputUtilTest {

  private static final int[] SPECIAL_CASES_TO_CHECK = {
    -1, 0, 1, 191, 192, 193,
    255, 256, 257,
    Short.MAX_VALUE, Short.MAX_VALUE + 1,
    Integer.MAX_VALUE, Integer.MIN_VALUE
  };

  @Test
  public void valueWrittenBy_writeINT_CouldBeReadBackBy_readINT() throws Exception {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1);
    for (int valueToCheck : SPECIAL_CASES_TO_CHECK) {
      buffer.clear();
      DataInputOutputUtil.writeINT(buffer, valueToCheck);
      buffer.clear();
      int valueRead = DataInputOutputUtil.readINT(buffer);
      assertEquals(
        "Value read must be equal to value written",
        valueToCheck,
        valueRead
      );
    }
  }

  @Test
  public void valueWrittenBy_writeINT_CouldBeReadBackBy_readINT_excessive() throws Exception {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1);
    for (int valueToCheck = Integer.MIN_VALUE; valueToCheck < Integer.MAX_VALUE; valueToCheck++) {
      buffer.clear();
      DataInputOutputUtil.writeINT(buffer, valueToCheck);
      buffer.clear();
      int valueRead = DataInputOutputUtil.readINT(buffer);
      assertEquals(
        "Value read must be equal to value written",
        valueToCheck,
        valueRead
      );
    }
  }

  private static final long[] SPECIAL_LONG_CASES_TO_CHECK = {
    System.currentTimeMillis(),
    -1, 0, 1, 191, 192, 193,
    255, 256, 257,
    Short.MAX_VALUE, Short.MAX_VALUE + 1,
    Integer.MAX_VALUE, Integer.MIN_VALUE,
    Long.MAX_VALUE, Long.MIN_VALUE
  };

  @Test
  public void valueWrittenBy_writeTIME_CouldBeReadBackBy_readTIME() throws Exception {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1);
    for (long valueToCheck : SPECIAL_LONG_CASES_TO_CHECK) {
      buffer.clear();
      DataInputOutputUtil.writeTIME(buffer, valueToCheck);

      buffer.clear();
      long valueRead = DataInputOutputUtil.readTIME(buffer);
      assertEquals(
        "Value read must be equal to value written",
        valueToCheck,
        valueRead
      );
    }
  }

  @Test
  public void valueWrittenBy_writeTIME_CouldBeReadBackBy_readTIME_random() throws Exception {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1);
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < Integer.MAX_VALUE; i++) {
      long valueToCheck = rnd.nextLong();

      buffer.clear();
      DataInputOutputUtil.writeTIME(buffer, valueToCheck);

      buffer.clear();
      long valueRead = DataInputOutputUtil.readTIME(buffer);

      assertEquals(
        "Value read must be equal to value written",
        valueToCheck,
        valueRead
      );
    }
  }


  private static final long[] SPECIAL_CASES_TO_CHECK_LONG = {
    -1, 0, 1, 191, 192, 193,
    255, 256, 257,
    Short.MAX_VALUE, Short.MAX_VALUE + 1,
    Integer.MAX_VALUE, Integer.MIN_VALUE,
    Long.MAX_VALUE / 8, Long.MAX_VALUE / 4, Long.MAX_VALUE / 2, Long.MAX_VALUE,
    Long.MIN_VALUE / 8, Long.MIN_VALUE / 4, Long.MIN_VALUE / 2, Long.MIN_VALUE,
  };

  @Test
  public void valueWrittenBy_writeLONG_CouldBeReadBackBy_readLONG() throws Exception {
    for (long valueToCheck : SPECIAL_CASES_TO_CHECK_LONG) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try (var out = new DataOutputStream(bos)) {
        DataInputOutputUtil.writeLONG(out, valueToCheck);
      }

      ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
      long valueRead = DataInputOutputUtil.readLONG(new DataInputStream(bis));

      assertEquals(
        "Value read must be equal to value written",
        valueToCheck,
        valueRead
      );
    }
  }

  @Test
  public void valueWrittenBy_writeLONG_CouldBeReadBackBy_readLONG_randomly() throws Exception {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < 1_000_000; i++) {
      long valueToCheck = rnd.nextLong();
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try (var out = new DataOutputStream(bos)) {
        DataInputOutputUtil.writeLONG(out, valueToCheck);
      }

      ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
      long valueRead = DataInputOutputUtil.readLONG(new DataInputStream(bis));

      assertEquals(
        "Value read must be equal to value written",
        valueToCheck,
        valueRead
      );
    }
  }
}