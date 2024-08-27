// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class LVTTest extends SingleClassesTestBase {
  @Override
  protected Map<String, String> getDecompilerOptions() {
    return Map.of(
      IFernflowerPreferences.DECOMPILE_INNER,"1",
      IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES,"1",
      IFernflowerPreferences.ASCII_STRING_CHARACTERS,"1",
      IFernflowerPreferences.LOG_LEVEL, "TRACE",
      IFernflowerPreferences.REMOVE_SYNTHETIC, "1",
      IFernflowerPreferences.REMOVE_BRIDGE, "1",
      IFernflowerPreferences.USE_DEBUG_VAR_NAMES, "1"
    );
  }

  @Override
  public void setUp() throws IOException {
      super.setUp();
      fixture.setCleanup(false);
  }

  @Test public void testLVT() { doTest("pkg/TestLVT"); }
  @Test public void testScoping() { doTest("pkg/TestLVTScoping"); }
  @Test public void testLVTComplex() { doTest("pkg/TestLVTComplex"); }
  @Test public void testVarType() { doTest("pkg/TestVarType"); }
  @Test public void testLoopMerging() { doTest("pkg/TestLoopMerging"); }
}
