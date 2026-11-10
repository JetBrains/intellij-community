// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.inspection;

import com.intellij.grazie.spellchecker.inspection.SpellcheckerInspectionTestCase;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class JavaSpellcheckerInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/inspections/spellchecker");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Registry.get("spellchecker.grazie.enabled").setValue(true, myFixture.getTestRootDisposable());
  }

  @Test
  public void testCorrectJava() { doTestInAllModes(); }
  @Test
  public void testTypoInJava() { doTestInAllModes(); }
  @Test
  public void testVarArg() { doTestInAllModes(); }
  @Test
  public void testJapanese() { doTestInAllModes(); }

  @Test
  public void testClassName() { doTestInAllModes(); }
  @Test
  public void testFieldName() { doTestInAllModes(); }
  @Test
  public void testMethodName() { doTestInAllModes(); }
  @Test
  public void testConstructorIgnored() { doTestInAllModes();}
  @Test
  public void testLocalVariableName() { doTestInAllModes(); }
  @Test
  public void testDocComment() { doTestInAllModes(); }
  @Test
  public void testStringLiteral() { doTestInAllModes(); }
  @Test
  public void testStringLiteralEscaping() { doTestInAllModes(); }
  @Test
  public void testSuppressions() { doTest(false); }

  // suppression by @NonNls
  @Test
  public void testMethodReturnTypeWithNonNls() { doTestInAllModes(); }
  @Test
  public void testMethodReturnTypeWithNonNlsReturnsLiteral() { doTestInAllModes(); }
  @Test
  public void testNonNlsField() { doTestInAllModes(); }
  @Test
  public void testNonNlsField2() { doTestInAllModes(); }
  @Test
  public void testNonNlsLocalVariable() { doTestInAllModes(); }
  @Test
  public void testNonNlsLocalVariableAndComment() { doTest(false); }
  @Test
  public void testNonNlsLocalVariableAndCommentDumb() { doTest(true); }
  @Test
  public void testFieldComment() { doTestInAllModes(); }
  @Test
  public void testDoNotCheckDerivedNames() { doTestInAllModes(); }
  @Test
  public void testSkipDateTime() { doTestInAllModes(); }

  private void doTestInAllModes() {
    doTest(false);
    doTest(true);
  }

  private void doTest(boolean inDumbMode) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(SpellcheckerInspectionTestCase.getInspectionTools());
    if (inDumbMode) {
      CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, myFixture.getTestRootDisposable());
      DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(),
                                                   () -> myFixture.testHighlighting(false, false, true, myFixture.getTestName(false) + ".java"));
    } else {
      myFixture.testHighlighting(false, false, true, myFixture.getTestName(false) + ".java");
    }
  }
}
