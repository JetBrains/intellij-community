// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.Test;

import java.io.IOException;

public class LiteralsTest {
  @Test
  public void testFloatLiterals() throws IOException {
    DecompilerTestFixture fixture = new DecompilerTestFixture();
    fixture.setUp(IFernflowerPreferences.LITERALS_AS_IS, "0");
    SingleClassesTest.doTest(fixture, "pkg/TestFloatLiterals");
    fixture.tearDown();
  }
}
