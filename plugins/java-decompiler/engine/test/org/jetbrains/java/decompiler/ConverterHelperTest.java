// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer.Type;
import org.jetbrains.java.decompiler.modules.renamer.ConverterHelper;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ConverterHelperTest {
  private static final String VALID_CLASS_NAME = "ValidClassName";
  private static final String VALID_FIELD_NAME = "validFieldName";
  private static final String VALID_METHOD_NAME = "validMethodName";
  private static final String VALID_FIELD_DESCRIPTOR = "I";
  private static final String VALID_METHOD_DESCRIPTOR = "()V";

  @Test public void testValidClassName() { doTestClassName(VALID_CLASS_NAME, false); }
  @Test public void testValidFieldName() { doTestFieldName(VALID_FIELD_NAME, VALID_FIELD_DESCRIPTOR, false); }
  @Test public void testValidMethodName() { doTestMethodName(VALID_METHOD_NAME, VALID_METHOD_DESCRIPTOR, false); }

  @Test public void testNullClassName() { doTestClassName(null, true); }
  @Test public void testNullFieldName() { doTestFieldName(null, VALID_FIELD_DESCRIPTOR, true); }
  @Test public void testNullMethodName() { doTestMethodName(null, VALID_METHOD_DESCRIPTOR, true); }

  @Test public void testEmptyClassName() { doTestClassName("", true); }
  @Test public void testEmptyFieldName() { doTestFieldName("", VALID_FIELD_DESCRIPTOR, true); }
  @Test public void testEmptyMethodName() { doTestMethodName("", VALID_METHOD_DESCRIPTOR, true); }

  @Test public void testShortClassName() { doTestClassName("C", true); }
  @Test public void testShortFieldName() { doTestFieldName("f", VALID_FIELD_DESCRIPTOR, true); }
  @Test public void testShortMethodName() { doTestMethodName("m", VALID_METHOD_DESCRIPTOR, true); }

  @Test public void testUnderscoreClassName() { doTestClassName("_", true); }
  @Test public void testUnderscoreFieldName() { doTestFieldName("_", VALID_FIELD_DESCRIPTOR, true); }
  @Test public void testUnderscoreMethodName() { doTestMethodName("_", VALID_METHOD_DESCRIPTOR, true); }

  @Test public void testKeywordClassName() { doTestClassName("public", true); }
  @Test public void testKeywordFieldName() { doTestFieldName("public", VALID_FIELD_DESCRIPTOR, true); }
  @Test public void testKeywordMethodName() { doTestMethodName("public", VALID_METHOD_DESCRIPTOR, true); }

  @Test public void testReservedWindowsNamespaceClassName() { doTestClassName("nul", true); }
  @Test public void testReservedWindowsNamespaceFieldName() { doTestFieldName("nul", VALID_FIELD_DESCRIPTOR, false); }
  @Test public void testReservedWindowsNamespaceName() { doTestMethodName("nul", VALID_METHOD_DESCRIPTOR, false); }

  @Test public void testLeadingDigitClassName() { doTestClassName("4identifier", true); }
  @Test public void testLeadingDigitFieldName() { doTestFieldName("4identifier", VALID_FIELD_DESCRIPTOR, true); }
  @Test public void testLeadingDigitMethodName() { doTestMethodName("4identifier", VALID_METHOD_DESCRIPTOR, true); }

  @Test public void testInvalidLeadingCharClassName() { doTestClassName("\uFEFFClassName", true); }
  @Test public void testInvalidLeadingCharFieldName() { doTestFieldName("\uFEFFfieldName", VALID_FIELD_DESCRIPTOR, true); }
  @Test public void testInvalidLeadingCharMethodName() { doTestMethodName("\uFEFFmethodName", VALID_METHOD_DESCRIPTOR, true); }

  @Test public void testInvalidMiddleCharClassName() { doTestClassName("Class\uFEFFName", true); }
  @Test public void testInvalidMiddleCharFieldName() { doTestFieldName("field\uFEFFName", VALID_FIELD_DESCRIPTOR, true); }
  @Test public void testInvalidMiddleCharMethodName() { doTestMethodName("method\uFEFFName", VALID_METHOD_DESCRIPTOR, true); }

  @Test public void testInvalidTrailingCharClassName() { doTestClassName("ClassName\uFEFF", true); }
  @Test public void testInvalidTrailingCharFieldName() { doTestFieldName("fieldName\uFEFF", VALID_FIELD_DESCRIPTOR, true); }
  @Test public void testInvalidTrailingCharMethodName() { doTestMethodName("methodName\uFEFF", VALID_METHOD_DESCRIPTOR, true); }

  @Test public void testLtInitGtClassName() { doTestClassName(CodeConstants.INIT_NAME, true); }
  @Test public void testLtInitGtFieldName() { doTestFieldName(CodeConstants.INIT_NAME, VALID_FIELD_DESCRIPTOR, true); }
  @Test public void testLtInitGtMethodName() { doTestMethodName(CodeConstants.INIT_NAME, VALID_METHOD_DESCRIPTOR, false); }

  @Test public void testLtClinitGtClassName() { doTestClassName(CodeConstants.CLINIT_NAME, true); }
  @Test public void testLtClinitGtFieldName() { doTestFieldName(CodeConstants.CLINIT_NAME, VALID_FIELD_DESCRIPTOR, true); }
  @Test public void testLtClinitGtMethodName() { doTestMethodName(CodeConstants.CLINIT_NAME, VALID_METHOD_DESCRIPTOR, false); }

  private static void doTestClassName(String className, boolean shallBeRenamed) {
    doTest(Type.ELEMENT_CLASS, className, null, null, shallBeRenamed);
  }

  private static void doTestFieldName(String element, String descriptor, boolean shallBeRenamed) {
    doTest(Type.ELEMENT_FIELD, VALID_CLASS_NAME, element, descriptor, shallBeRenamed);
  }

  private static void doTestMethodName(String element, String descriptor, boolean shallBeRenamed) {
    doTest(Type.ELEMENT_METHOD, VALID_CLASS_NAME, element, descriptor, shallBeRenamed);
  }

  private static void doTest(Type elementType, String className, String element, String descriptor, boolean shallBeRenamed) {
    boolean result = new ConverterHelper().toBeRenamed(elementType, className, element, descriptor);
    String assertionMessage = shallBeRenamed ? "Identifier { %s, %s, %s, %s } shall be renamed" : "Identifier { %s, %s, %s, %s } shall not be renamed";

    assertTrue(String.format(assertionMessage, elementType.toString(), className, element, descriptor), result == shallBeRenamed);
  }
}