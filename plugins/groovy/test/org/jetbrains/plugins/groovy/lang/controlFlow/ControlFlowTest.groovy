// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.controlFlow

import com.intellij.openapi.editor.SelectionModel
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author ven
 */
class ControlFlowTest extends LightCodeInsightFixtureTestCase {
  final String basePath = TestUtils.testDataPath + "groovy/controlFlow/"

  void testAssignment() { doTest() }

  void testClosure1() { doTest() }

  void testComplexAssign() { doTest() }

  void testFor1() { doTest() }

  void testForeach1() { doTest() }

  void testGrvy1497() { doTest() }

  void testIf1() { doTest() }

  void testMultipleAssignment() { doTest() }

  void testNested() { doTest() }

  void testReturn() { doTest() }

  void testSwitch1() { doTest() }

  void testSwitch2() { doTest() }

  void testSwitch3() { doTest() }

  void testSwitch4() { doTest() }

  void testSwitch5() { doTest() }

  void testNestedSwitch1() { doTest() }

  void testNestedSwitch2() { doTest() }

  void testNestedSwitch3() { doTest() }

  void testNestedSwitch4() { doTest() }

  void testSwitchWithinFor() { doTest() }

  void testSwitchWithinLabeledFor() { doTest() }

  void testForWithinSwitchWithinFor() { doTest() }

  void testThrow1() { doTest() }

  void testThrowInCatch() { doTest() }

  void testTry1() { doTest() }

  void testTry2() { doTest() }

  void testTry3() { doTest() }

  void testTry4() { doTest() }

  void testTry5() { doTest() }

  void testTry6() { doTest() }

  void testTry7() { doTest() }

  void testTry8() { doTest() }

  void testTry9() { doTest() }

  void testTry10() { doTest() }

  void testWhile1() { doTest() }

  void testWhile2() { doTest() }

  void testWhileNonConstant() { doTest() }

  void testIfInstanceofElse() { doTest() }

  void testIfNegatedInstanceofElse() { doTest() }

  void testReturnMapFromClosure() { doTest() }

  void testSwitchInTryWithThrows() { doTest() }

  void testClosure() { doTest() }

  void testAnonymous() { doTest() }

  void testSomeCatches() { doTest() }

  void testOrInReturn() { doTest() }

  void testVarInString() { doTest() }

  void testMayBeStaticWithCondition() { doTest() }

  void testAssert0() { doTest() }

  void testAssert1() { doTest() }

  void testAssert2() { doTest() }

  void testAssert3() { doTest() }

  void testAssert4() { doTest() }

  void testStringInjectionWithParam() { doTest() }

  void testUnaryExpressionInReturn() { doTest() }

  void testBinaryExpressionInReturn() { doTest() }

  void testPendingFromIf() { doTest() }

  void testSwitchWithEmptyCaseBeforeDefault() { doTest() }

  void testUnfinishedAssignment() { doTest() }

  void doTest() {
    final List<String> input = TestUtils.readInput(testDataPath + getTestName(true) + ".test")

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, input.get(0))

    final GroovyFile file = (GroovyFile)myFixture.file
    final SelectionModel model = myFixture.editor.selectionModel
    final PsiElement start = file.findElementAt(model.hasSelection() ? model.selectionStart : 0)
    final PsiElement end = file.findElementAt(model.hasSelection() ? model.selectionEnd - 1 : file.textLength - 1)
    final GrControlFlowOwner owner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), GrControlFlowOwner, false)
    final Instruction[] instructions = new ControlFlowBuilder(project).buildControlFlow(owner)
    final String cf = ControlFlowUtils.dumpControlFlow(instructions)
    assertEquals(input.get(1).trim(), cf.trim())
  }

}
