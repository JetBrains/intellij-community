// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.controlFlow;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ControlFlowTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/controlFlow/");
  }

  @Test
  public void testAssignment() {
    doTest();
  }

  @Test
  public void testClosure1() {
    doTest();
  }

  @Test
  public void testComplexAssign() {
    doTest();
  }

  @Test
  public void testFor1() {
    doTest();
  }

  @Test
  public void testFor2() {
    doTest();
  }

  @Test
  public void testForeach1() {
    doTest();
  }

  @Test
  public void testForeach2() {
    doTest();
  }

  @Test
  public void testGrvy1497() {
    doTest();
  }

  @Test
  public void testIf1() {
    doTest();
  }

  @Test
  public void testMultipleAssignment() {
    doTest();
  }

  @Test
  public void testNested() {
    doTest();
  }

  @Test
  public void testReturn() {
    doTest();
  }

  @Test
  public void testSwitch1() {
    doTest();
  }

  @Test
  public void testSwitch2() {
    doTest();
  }

  @Test
  public void testSwitch3() {
    doTest();
  }

  @Test
  public void testSwitch4() {
    doTest();
  }

  @Test
  public void testSwitch5() {
    doTest();
  }

  @Test
  public void testNestedSwitch1() {
    doTest();
  }

  @Test
  public void testNestedSwitch2() {
    doTest();
  }

  @Test
  public void testNestedSwitch3() {
    doTest();
  }

  @Test
  public void testNestedSwitch4() {
    doTest();
  }

  @Test
  public void testSwitchexpr1() {
    doTest();
  }

  @Test
  public void testSwitchWithinFor() {
    doTest();
  }

  @Test
  public void testSwitchWithinLabeledFor() {
    doTest();
  }

  @Test
  public void testForWithinSwitchWithinFor() {
    doTest();
  }

  @Test
  public void testThrow1() {
    doTest();
  }

  @Test
  public void testThrowInCatch() {
    doTest();
  }

  @Test
  public void testTry1() {
    doTest();
  }

  @Test
  public void testTry2() {
    doTest();
  }

  @Test
  public void testTry3() {
    doTest();
  }

  @Test
  public void testTry4() {
    doTest();
  }

  @Test
  public void testTry5() {
    doTest();
  }

  @Test
  public void testTry6() {
    doTest();
  }

  @Test
  public void testTry7() {
    doTest();
  }

  @Test
  public void testTry8() {
    doTest();
  }

  @Test
  public void testTry9() {
    doTest();
  }

  @Test
  public void testTry10() {
    doTest();
  }

  @Test
  public void testTry11() {
    doTest();
  }

  @Test
  public void testTryResources() {
    doTest();
  }

  @Test
  public void testWhile1() {
    doTest();
  }

  @Test
  public void testWhile2() {
    doTest();
  }

  @Test
  public void testWhileNonConstant() {
    doTest();
  }

  @Test
  public void testIfInstanceofElse() {
    doTest();
  }

  @Test
  public void testIfNegatedInstanceofElse() {
    doTest();
  }

  @Test
  public void testIfInstanceofOr() {
    doTest();
  }

  @Test
  public void testIfNullOrInstanceof() {
    doTest();
  }

  @Test
  public void testReturnMapFromClosure() {
    doTest();
  }

  @Test
  public void testSwitchInTryWithThrows() {
    doTest();
  }

  @Test
  public void testClosure() {
    doTest();
  }

  @Test
  public void testAnonymous() {
    doTest();
  }

  @Test
  public void testSomeCatches() {
    doTest();
  }

  @Test
  public void testOrInReturn() {
    doTest();
  }

  @Test
  public void testVarInString() {
    doTest();
  }

  @Test
  public void testMayBeStaticWithCondition() {
    doTest();
  }

  @Test
  public void testAssert0() {
    doTest();
  }

  @Test
  public void testAssert1() {
    doTest();
  }

  @Test
  public void testAssert2() {
    doTest();
  }

  @Test
  public void testAssert3() {
    doTest();
  }

  @Test
  public void testAssert4() {
    doTest();
  }

  @Test
  public void testStringInjectionWithParam() {
    doTest();
  }

  @Test
  public void testUnaryExpressionInReturn() {
    doTest();
  }

  @Test
  public void testBinaryExpressionInReturn() {
    doTest();
  }

  @Test
  public void testPendingFromIf() {
    doTest();
  }

  @Test
  public void testSwitchWithEmptyCaseBeforeDefault() {
    doTest();
  }

  @Test
  public void testUnfinishedAssignment() {
    doTest();
  }

  @Test
  public void testImplication() {
    doTest();
  }

  public void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String path = myFixture.getTestName(true) + ".test";
    final List<String> input = TestUtils.readInput(myFixture.getTestDataPath() + path);

    final String code = input.getFirst();
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, code);

    ReadAction.runBlocking(() -> {
      final GroovyFile file = (GroovyFile)myFixture.getFile();
      final SelectionModel model = myFixture.getEditor().getSelectionModel();
      final PsiElement start = file.findElementAt(model.hasSelection() ? model.getSelectionStart() : 0);
      final PsiElement end = file.findElementAt(model.hasSelection() ? model.getSelectionEnd() - 1 : file.getTextLength() - 1);
      final GrControlFlowOwner owner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), GrControlFlowOwner.class, false);
      final String cf = ControlFlowUtils.dumpControlFlow(owner.getGroovyControlFlow());
      assertEquals(input.get(1).trim(), cf.trim());
    });
  }
}
