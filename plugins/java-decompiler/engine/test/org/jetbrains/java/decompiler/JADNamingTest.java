// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.Test;

import java.util.Map;

public class JADNamingTest extends SingleClassesTestBase {

    @Override
    protected Map<String, String> getDecompilerOptions() {
      return Map.of(
        IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1",
        IFernflowerPreferences.DUMP_ORIGINAL_LINES, "1",
        IFernflowerPreferences.USE_JAD_VARNAMING, "1"
      );
    }

    @Test public void testClassFields() { doTest("pkg/TestJADNaming"); }

}
