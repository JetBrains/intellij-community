/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
