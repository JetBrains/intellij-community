// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.controlFlow;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class ControlFlowTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/controlFlow/";
  }

  public void testAssignment() { doTest(); }

  public void testClosure1() { doTest(); }

  public void testComplexAssign() { doTest(); }

  public void testFor1() { doTest(); }

  public void testFor2() { doTest(); }

  public void testForeach1() { doTest(); }

  public void testGrvy1497() { doTest(); }

  public void testIf1() { doTest(); }

  public void testMultipleAssignment() { doTest(); }

  public void testNested() { doTest(); }

  public void testReturn() { doTest(); }

  public void testSwitch1() { doTest(); }

  public void testSwitch2() { doTest(); }

  public void testSwitch3() { doTest(); }

  public void testSwitch4() { doTest(); }

  public void testSwitch5() { doTest(); }

  public void testNestedSwitch1() { doTest(); }

  public void testNestedSwitch2() { doTest(); }

  public void testNestedSwitch3() { doTest(); }

  public void testNestedSwitch4() { doTest(); }

  public void testSwitchexpr1() { doTest(); }

  public void testSwitchWithinFor() { doTest(); }

  public void testSwitchWithinLabeledFor() { doTest(); }

  public void testForWithinSwitchWithinFor() { doTest(); }

  public void testThrow1() { doTest(); }

  public void testThrowInCatch() { doTest(); }

  public void testTry1() { doTest(); }

  public void testTry2() { doTest(); }

  public void testTry3() { doTest(); }

  public void testTry4() { doTest(); }

  public void testTry5() { doTest(); }

  public void testTry6() { doTest(); }

  public void testTry7() { doTest(); }

  public void testTry8() { doTest(); }

  public void testTry9() { doTest(); }

  public void testTry10() { doTest(); }

  public void testTry11() { doTest(); }

  public void testTryResources() { doTest(); }

  public void testWhile1() { doTest(); }

  public void testWhile2() { doTest(); }

  public void testWhileNonConstant() { doTest(); }

  public void testIfInstanceofElse() { doTest(); }

  public void testIfNegatedInstanceofElse() { doTest(); }

  public void testIfInstanceofOr() { doTest(); }

  public void testIfNullOrInstanceof() { doTest(); }

  public void testReturnMapFromClosure() { doTest(); }

  public void testSwitchInTryWithThrows() { doTest(); }

  public void testClosure() { doTest(); }

  public void testAnonymous() { doTest(); }

  public void testSomeCatches() { doTest(); }

  public void testOrInReturn() { doTest(); }

  public void testVarInString() { doTest(); }

  public void testMayBeStaticWithCondition() { doTest(); }

  public void testAssert0() { doTest(); }

  public void testAssert1() { doTest(); }

  public void testAssert2() { doTest(); }

  public void testAssert3() { doTest(); }

  public void testAssert4() { doTest(); }

  public void testStringInjectionWithParam() { doTest(); }

  public void testUnaryExpressionInReturn() { doTest(); }

  public void testBinaryExpressionInReturn() { doTest(); }

  public void testPendingFromIf() { doTest(); }

  public void testSwitchWithEmptyCaseBeforeDefault() { doTest(); }

  public void testUnfinishedAssignment() { doTest(); }

  public void doTest() {
    final String path = getTestName(true) + ".test";
    final List<String> input = TestUtils.readInput(getTestDataPath() + path);

    final String code = input.get(0);
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, code);

    final GroovyFile file = (GroovyFile)myFixture.getFile();
    final SelectionModel model = myFixture.getEditor().getSelectionModel();
    final PsiElement start = file.findElementAt(model.hasSelection() ? model.getSelectionStart() : 0);
    final PsiElement end = file.findElementAt(model.hasSelection() ? model.getSelectionEnd() - 1 : file.getTextLength() - 1);
    final GrControlFlowOwner owner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), GrControlFlowOwner.class, false);
    final String cf = ControlFlowUtils.dumpControlFlow(ControlFlowUtils.getGroovyControlFlow(owner));
    TestCase.assertEquals(input.get(1).trim(), cf.trim());
  }
}
