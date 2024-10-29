// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.junit.Test;

import java.util.Map;

import static junit.framework.TestCase.assertEquals;

public class TrimHelperTest extends SingleClassesTestBase {

  @Override
  protected Map<String, String> getDecompilerOptions() {
    return Map.of(IFernflowerPreferences.STANDARDIZE_FLOATING_POINT_NUMBERS, "1"
    );
  }

  @Test
  public void testTrimFloat() {
    assertEquals("1.0123457", ConstExprent.trimFloat("1.012345678f", 1.012345678f));
    assertEquals("1.1023457", ConstExprent.trimFloat("1.102345678f", 1.102345678f));
    assertEquals("1.0012345", ConstExprent.trimFloat("1.0012345678f", 1.0012345678f));
    assertEquals("1.1012345", ConstExprent.trimFloat("1.1012345678f", 1.1012345678f));
    assertEquals("1.1123457", ConstExprent.trimFloat("1.112345678f", 1.112345678f));
  }

  @Test
  public void testTrimDouble() {
    assertEquals("1.0123456781234567", ConstExprent.trimDouble("1.01234567812345678", 1.01234567812345678));
    assertEquals("1.0012345678123457", ConstExprent.trimDouble("1.001234567812345678", 1.001234567812345678));
    assertEquals("1.1012345678123456", ConstExprent.trimDouble("1.101234567812345678", 1.101234567812345678));
    assertEquals("1.1023456781234568", ConstExprent.trimDouble("1.102345678123456789", 1.102345678123456789));
    assertEquals("1.1123456781234568", ConstExprent.trimDouble("1.11234567812345678", 1.11234567812345678));
  }
}