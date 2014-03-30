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
package org.jetbrains.plugins.groovy

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GrAllVarsInitializedPolicy
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.FragmentVariableInfos
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsCollector
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @auther ven
 */
public class ReachingDefsTest extends LightCodeInsightFixtureTestCase {

  String basePath = TestUtils.testDataPath + 'groovy/reachingDefs/'

  public void testAssign() { doTest() }
  public void testClosure() { doTest() }
  public void testClosure1() { doTest() }
  public void testEm1() { doTest() }
  public void testEm2() { doTest() }
  public void testEm3() { doTest() }
  public void testIf1() { doTest() }
  public void testInner() { doTest() }
  public void testLocal1() { doTest() }
  public void testLocal2() { doTest() }
  public void testSimpl1() { doTest() }
  public void testSimpl2() { doTest() }
  public void testSimpl3() { doTest() }
  public void testWhile1() { doTest() }

  public void doTest() {
    final List<String> data = TestUtils.readInput(testDataPath + getTestName(true) + ".test")
    String text = data.get(0)

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text)

    int selStart = myFixture.editor.selectionModel.selectionStart
    int selEnd = myFixture.editor.selectionModel.selectionEnd

    final GroovyFile file = (GroovyFile)myFixture.file
    final PsiElement start = file.findElementAt(selStart)
    final PsiElement end = file.findElementAt(selEnd - 1)
    final GrControlFlowOwner owner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), GrControlFlowOwner, false)
    assert owner != null
    GrStatement firstStatement = getStatement(start, owner)
    GrStatement lastStatement = getStatement(end, owner)

    final GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(firstStatement)
    final ControlFlowBuilder flowBuilder = new ControlFlowBuilder(firstStatement.getProject(), GrAllVarsInitializedPolicy.getInstance())
    final Instruction[] flow = flowBuilder.buildControlFlow(flowOwner)
    final FragmentVariableInfos fragmentVariableInfos = ReachingDefinitionsCollector.obtainVariableFlowInformation(firstStatement, lastStatement, flowOwner, flow)

    assertEquals(data.get(1), dumpInfo(fragmentVariableInfos).trim())
  }

  private static String dumpInfo(FragmentVariableInfos fragmentVariableInfos) {
    StringBuilder builder = new StringBuilder()
    builder.append("input:\n")
    for (VariableInfo info : fragmentVariableInfos.inputVariableNames) {
      builder.append(info.name).append("\n")
    }

    builder.append("output:\n")
    for (VariableInfo info : fragmentVariableInfos.outputVariableNames) {
      builder.append(info.name).append("\n")
    }

    return builder.toString()
  }

  private static GrStatement getStatement(@NotNull PsiElement element, PsiElement context) {
    while (element.parent != context) {
      element = element.parent
      assert element != null
    }

    return (GrStatement) element
  }

}
