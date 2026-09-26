// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip;

import org.jetbrains.java.decompiler.roundTrip.fixtures.java.JavaDecompilerRoundTripTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

public class JavaWithoutLVTDecompilerTest extends JavaDecompilerRoundTripTestCase {
  @Override
  protected String testCaseDir() {
    return "java/withoutLVT";
  }

  @Test
  public void testDeadVariableCode() {
    doTest("DeadVariableCode");
  }


  @Override
  protected void doTest(String sourceFile, String... companionFileSystemItems) {
    super.doTest(sourceFile, List.of("-g:none"), companionFileSystemItems);
  }
}
