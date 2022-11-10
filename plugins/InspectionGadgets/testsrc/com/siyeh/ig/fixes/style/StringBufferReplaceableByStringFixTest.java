// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.StringBufferReplaceableByStringInspection;

public class StringBufferReplaceableByStringFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new StringBufferReplaceableByStringInspection());
    myRelativePath = "style/replace_with_string";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

  private void doTestFor(String builderClassName) {
    doTest(CommonQuickFixBundle.message("fix.replace.x.with.y", builderClassName, "String"));
  }

  public void testSimpleStringBuffer() { doTestFor("StringBuffer"); }
  public void testStringBuilderAppend() { doTestFor("StringBuilder"); }
  public void testStringBuilderAppendSubString() { doTestFor("StringBuilder"); }
  public void testStringBufferVariable() { doTestFor("StringBuffer"); }
  public void testStringBufferVariable2() { doTestFor("StringBuffer"); }
  public void testStartsWithPrimitive() { doTestFor("StringBuffer"); }
  public void testStartsWithPrimitive2() { doTestFor("StringBuilder"); }
  public void testStartsWithPrimitive3() { doTestFor("StringBuilder"); }
  public void testStartsWithPrimitive4() { doTestFor("StringBuilder"); }
  public void testStartsWithPrimitive5() { doTestFor("StringBuilder"); }
  public void testStartsWithPrimitive6() { doTestFor("StringBuilder"); }
  public void testPrecedence() { doTestFor("StringBuilder"); }
  public void testPrecedence2() { doTestFor("StringBuilder"); }
  public void testPrecedence3() { doTestFor("StringBuilder"); }
  public void testPrecedence4() { doTestFor("StringBuilder"); }
  public void testNonString1() { doTestFor("StringBuilder"); }
  public void testNonString2() { doTestFor("StringBuilder"); }
  public void testMarathon() { doTestFor("StringBuilder"); }
  public void testArray() { doTestFor("StringBuilder"); }
  public void testArray2() { doTestFor("StringBuilder"); }
  public void testArray3() { doTestFor("StringBuilder"); }
  public void testConstructorArgument() { doTestFor("StringBuilder"); }
  public void testConstructorArgument2() { doTestFor("StringBuilder"); }
  public void testNoConstructorArgument() { doTestFor("StringBuilder"); }
  public void testCharLiteral() { doTestFor("StringBuilder"); }
  public void testEscape() { doTestFor("StringBuilder"); }
  public void testUnescape() { doTestFor("StringBuilder"); }
  public void testMethodCallOnString() { doTestFor("StringBuilder"); }
  public void testComplex1() { doTestFor("StringBuilder"); }
  public void testComplex2() { doTestFor("StringBuilder"); }
  public void testLinebreaks() { doTestFor("StringBuilder"); }
  public void testSlashSlashInLiteral() { doTestFor("StringBuilder"); }
  public void testHelperVariable() { doTestFor("StringBuilder"); }
  public void testComment1() { doTestFor("StringBuffer"); }
  public void testComment2() { doTestFor("StringBuilder"); }
  public void testComment3() { doTestFor("StringBuffer"); }
  public void testComment4() { doTestFor("StringBuilder"); }
  public void testComment5() { doTestFor("StringBuilder"); }
  public void testMultipleComments() { doTestFor("StringBuilder"); }
  public void testMultipleCommentsMissingSemicolon() { doTestFor("StringBuilder"); }
  public void testMultipleCommentsNoWhitespace() { doTestFor("StringBuilder"); }
  public void testImplicitToString() { doTestFor("StringBuilder"); }
  public void testImplicitToString2() { doTestFor("StringBuilder"); }
  public void testImplicitToString3() { doTestFor("StringBuilder"); }
  public void testJoiner() { doTestFor("StringJoiner"); }
  public void testJoiner2() { doTestFor("StringJoiner"); }
  public void testCharacterPlusAppend() { doTestFor("StringBuilder"); }
  public void testSideEffect() { doTestFor("StringBuilder"); }
  public void testSideEffect2() { doTestFor("StringBuilder"); }
  public void testSideEffect3() { doTestFor("StringBuilder"); }

  public void testComplexSignOnNextLine() {
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = true;
    try {
      doTestFor("StringBuilder");
    }
    finally {
      settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = false;
    }
  }

}
