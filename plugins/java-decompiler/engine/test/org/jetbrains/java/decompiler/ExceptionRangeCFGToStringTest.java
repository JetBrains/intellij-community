// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.code.cfg.ExceptionRangeCFG;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

public class ExceptionRangeCFGToStringTest {

  private DecompilerTestFixture fixture;

  @Before
  public void setUp() throws IOException {
    fixture = new DecompilerTestFixture();
    fixture.setUp(
      IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1",
      IFernflowerPreferences.DUMP_ORIGINAL_LINES, "1",
      IFernflowerPreferences.IGNORE_INVALID_BYTECODE, "1",
      IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES, "1",
      IFernflowerPreferences.NEW_LINE_SEPARATOR, "1"
    );
  }

  @After
  public void tearDown() {
    fixture.tearDown();
    fixture = null;
  }

  @Test
  public void testTryExceptionRange() {
    BasicBlock protectedBlock = new BasicBlock(7);
    BasicBlock handlerBlock = new BasicBlock(11);
    ExceptionRangeCFG exceptionRange = new ExceptionRangeCFG(
      Collections.singletonList(protectedBlock), handlerBlock, Collections.singletonList("java/lang/Exception")
    );

    Assert.assertEquals(
      "exceptionType: java/lang/Exception\n" +
      "handler: 11\n" +
      "range: 7 \n",
      exceptionRange.toString()
    );
  }

  @Test
  public void testFinallyExceptionRange() {
    BasicBlock protectedBlock = new BasicBlock(7);
    BasicBlock handlerBlock = new BasicBlock(11);
    ExceptionRangeCFG exceptionRange = new ExceptionRangeCFG(
      Collections.singletonList(protectedBlock), handlerBlock, null
    );

    Assert.assertEquals(
      "exceptionType: null\n" +
      "handler: 11\n" +
      "range: 7 \n",
      exceptionRange.toString()
    );
  }

}
