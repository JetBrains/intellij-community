// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class DataInputOutputUtilRtTest {

  private static final int[] SPECIAL_CASES_TO_CHECK = {
    -1, 0, 1, 191, 192, 193,
    255, 256, 257,
    Short.MAX_VALUE, Short.MAX_VALUE + 1,
    Integer.MAX_VALUE, Integer.MIN_VALUE
  };

  @Test
  public void valueWrittenBy_writeINT_CouldBeReadBackBy_readINT() throws Exception {
    final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1);
    for (int valueToCheck : SPECIAL_CASES_TO_CHECK) {
      buffer.clear();
      DataInputOutputUtilRt.writeINT(buffer, valueToCheck);
      buffer.clear();
      final int valueRead = DataInputOutputUtilRt.readINT(buffer);
      assertEquals(
        "Value read must be equal to value written",
        valueToCheck,
        valueRead
      );
    }
  }

  @Test
  public void valueWrittenBy_writeINT_CouldBeReadBackBy_readINT_excessive() throws Exception {
    final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1);
    for (int valueToCheck = Integer.MIN_VALUE; valueToCheck < Integer.MAX_VALUE; valueToCheck++) {
      buffer.clear();
      DataInputOutputUtilRt.writeINT(buffer, valueToCheck);
      buffer.clear();
      final int valueRead = DataInputOutputUtilRt.readINT(buffer);
      assertEquals(
        "Value read must be equal to value written",
        valueToCheck,
        valueRead
      );
    }
  }
}