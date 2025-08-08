// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.inspection;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class JavaSpellcheckerInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/inspections/spellchecker";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("spellchecker.grazie.enabled").setValue(true, getTestRootDisposable());
  }

  public void testCorrectJava() { doTestInAllModes(); }
  public void testTypoInJava() { doTestInAllModes(); }
  public void testVarArg() { doTestInAllModes(); }
  public void testJapanese() { doTestInAllModes(); }

  public void testClassName() { doTestInAllModes(); }
  public void testFieldName() { doTestInAllModes(); }
  public void testMethodName() { doTestInAllModes(); }
  public void testConstructorIgnored() { doTestInAllModes();}
  public void testLocalVariableName() { doTestInAllModes(); }
  public void testDocComment() { doTestInAllModes(); }
  public void testStringLiteral() { doTestInAllModes(); }
  public void testStringLiteralEscaping() { doTestInAllModes(); }
  public void testSuppressions() { doTest(false); }

  // suppression by @NonNls
  public void testMethodReturnTypeWithNonNls() { doTestInAllModes(); }
  public void testMethodReturnTypeWithNonNlsReturnsLiteral() { doTestInAllModes(); }
  public void testNonNlsField() { doTestInAllModes(); }
  public void testNonNlsField2() { doTestInAllModes(); }
  public void testNonNlsLocalVariable() { doTestInAllModes(); }
  public void testNonNlsLocalVariableAndComment() { doTestInAllModes(); }
  public void testFieldComment() { doTestInAllModes(); }
  public void testDoNotCheckDerivedNames() { doTestInAllModes(); }
  public void testSkipDateTime() { doTestInAllModes(); }

  private void doTestInAllModes() {
    doTest(false);
    doTest(true);
  }

  private void doTest(boolean inDumbMode) {
    myFixture.enableInspections(SpellcheckerInspectionTestCase.getInspectionTools());
    if (inDumbMode) {
      ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).mustWaitForSmartMode(false, getTestRootDisposable());
      DumbModeTestUtils.runInDumbModeSynchronously(getProject(),
                                                   () -> myFixture.testHighlighting(false, false, true, getTestName(false) + ".java"));
    } else {
      myFixture.testHighlighting(false, false, true, getTestName(false) + ".java");
    }
  }
}
