/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class ControlFlowTest extends LightCodeInsightFixtureTestCase {
  final String basePath = TestUtils.testDataPath + "groovy/controlFlow/"

  public void testAssignment() { doTest(); }
  public void testClosure1() { doTest(); }
  public void testComplexAssign() { doTest(); }
  public void testFor1() { doTest(); }
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
  public void testNestedSwitch1() { doTest() };
  public void testNestedSwitch2() { doTest() };
  public void testNestedSwitch3() { doTest() };
  public void testNestedSwitch4() { doTest() };
  public void testSwitchWithinFor() {doTest() };
  public void testSwitchWithinLabeledFor() { doTest() };
  public void testForWithinSwitchWithinFor() { doTest() };
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
  public void testWhile1() { doTest(); }
  public void testWhile2() { doTest(); }
  public void testWhileNonConstant() { doTest(); }
  public void testIfInstanceofElse() { doTest(); }
  public void testReturnMapFromClosure() {doTest();}
  public void testSwitchInTryWithThrows() {doTest();}
  public void testClosure() {doTest();}
  public void testAnonymous() {doTest();}
  public void testSomeCatches() {doTest();}
  public void testOrInReturn() {doTest();}
  public void testVarInString() {doTest();}
  public void testMayBeStaticWithCondition() {doTest()}
  public void testAssert0() { doTest() }
  public void testAssert1() { doTest() }
  public void testAssert2() { doTest() }
  public void testAssert3() { doTest() }
  public void testAssert4() { doTest() }
  public void testStringInjectionWithParam() { doTest() }
  public void testUnaryExpressionInReturn() { doTest() }
  public void testBinaryExpressionInReturn() { doTest() }
  public void testPendingFromIf() { doTest() }
  public void testSwitchWithEmptyCaseBeforeDefault() { doTest() }
  public void testUnfinishedAssignment() { doTest() }

  public void doTest() {
    final List<String> input = TestUtils.readInput(testDataPath + getTestName(true) + ".test");

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, input.get(0));

    final GroovyFile file = (GroovyFile)myFixture.file;
    final SelectionModel model = myFixture.editor.selectionModel;
    final PsiElement start = file.findElementAt(model.hasSelection() ? model.selectionStart : 0);
    final PsiElement end = file.findElementAt(model.hasSelection() ? model.selectionEnd - 1 : file.textLength - 1);
    final GrControlFlowOwner owner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), GrControlFlowOwner, false);
    final Instruction[] instructions = new ControlFlowBuilder(project).buildControlFlow(owner);
    final String cf = ControlFlowUtils.dumpControlFlow(instructions);
    assertEquals(input.get(1).trim(), cf.trim());
  }

}
