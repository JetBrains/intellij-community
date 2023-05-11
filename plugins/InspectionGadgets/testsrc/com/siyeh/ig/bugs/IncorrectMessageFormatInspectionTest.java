// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class IncorrectMessageFormatInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/bugs/incorrect_message_format";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @SuppressWarnings({"NonFinalUtilityClass", "UtilityClassWithPublicConstructor"})
  private void doTest() {
    myFixture.addClass("""
                       package java.text;
                       public class MessageFormat{
                       public MessageFormat(String pattern) {}
                       public static String format(String pattern, Object ... arguments) {return null;}}""");
    myFixture.enableInspections(new IncorrectMessageFormatInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testIncorrectMessageFormat() {
    doTest();
  }
}
