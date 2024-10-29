// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.Map;

public class HideRecordConstructorSingleClassesTest extends SingleClassesTestBase {
  /*
   * Set individual test duration time limit to 60 seconds.
   * This will help us to test bugs hanging decompiler.
   */
  @Rule
  public Timeout globalTimeout = Timeout.seconds(60);

  @Override
  protected Map<String, String> getDecompilerOptions() {
    return Map.of(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1",
                  IFernflowerPreferences.DUMP_ORIGINAL_LINES, "1",
                  IFernflowerPreferences.IGNORE_INVALID_BYTECODE, "1",
                  IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES, "1",
                  IFernflowerPreferences.CONVERT_PATTERN_SWITCH, "1",
                  IFernflowerPreferences.CONVERT_RECORD_PATTERN, "1",
                  IFernflowerPreferences.INLINE_SIMPLE_LAMBDAS, "1",
                  IFernflowerPreferences.CHECK_CLOSABLE_INTERFACE, "0",
                  IFernflowerPreferences.HIDE_RECORD_CONSTRUCTOR_AND_GETTERS, "1"
    );
  }

  @Test public void testHideConstructorRecordEmpty() { doTest("records/TestHideConstructorRecordEmpty"); }
  @Test public void testHideConstructorRecordSimple() { doTest("records/TestHideConstructorRecordSimple"); }
  @Test public void testHideConstructorRecordVararg() { doTest("records/TestHideConstructorRecordVararg"); }
  @Test public void testHideConstructorRecordAnno() { doTest("records/TestHideConstructorRecordAnno"); }
  @Test public void testHideConstructorRecordDifferentTypes() { doTest("records/TestHideConstructorRecordDifferentTypes"); }
  @Test public void testHideConstructorRecordAnnoConstructor() { doTest("records/TestHideConstructorRecordAnnoConstructor"); }
  @Test public void testHideConstructorRecordAnnoGetter() { doTest("records/TestHideConstructorRecordAnnoGetter"); }
  @Test public void testHideConstructorRecordAnnoComponentType() { doTest("records/TestHideConstructorRecordAnnoComponentType"); }
  @Test public void testHideConstructorRecordAnnoParameterType() { doTest("records/TestHideConstructorRecordAnnoParameterType"); }
  @Test public void testHideConstructorRecordAnnoGetterType() { doTest("records/TestHideConstructorRecordAnnoGetterType"); }
  @Test public void testHideConstructorRecordAnnoParameterAndType() { doTest("records/TestHideConstructorRecordAnnoParameterAndType"); }

  //record + parameter scope of use
}
