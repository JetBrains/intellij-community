// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GrAllVarsInitializedPolicy
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GroovyControlFlow
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.FragmentVariableInfos
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsCollector
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @auther ven
 */
class ReachingDefsTest extends LightJavaCodeInsightFixtureTestCase {

  String basePath = TestUtils.testDataPath + 'groovy/reachingDefs/'

  void testAssign() { doTest() }

  void testClosure() { doTest() }

  void testClosure1() { doTest() }

  void testEm1() { doTest() }

  void testEm2() { doTest() }

  void testEm3() { doTest() }

  void testIf1() { doTest() }

  void testInner() { doTest() }

  void testLocal1() { doTest() }

  void testLocal2() { doTest() }

  void testSimpl1() { doTest() }

  void testSimpl2() { doTest() }

  void testSimpl3() { doTest() }

  void testWhile1() { doTest() }

  void doTest() {
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
    final GroovyControlFlow flow = ControlFlowBuilder.buildControlFlow(flowOwner, GrAllVarsInitializedPolicy.getInstance())
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
