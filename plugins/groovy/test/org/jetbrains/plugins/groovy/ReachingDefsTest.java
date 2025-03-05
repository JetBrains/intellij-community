// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GrAllVarsInitializedPolicy;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GroovyControlFlow;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.FragmentVariableInfos;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsCollector;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class ReachingDefsTest extends LightJavaCodeInsightFixtureTestCase {
  public void testAssign() { doTest(); }

  public void testClosure() { doTest(); }

  public void testClosure1() { doTest(); }

  public void testEm1() { doTest(); }

  public void testEm2() { doTest(); }

  public void testEm3() { doTest(); }

  public void testIf1() { doTest(); }

  public void testInner() { doTest(); }

  public void testLocal1() { doTest(); }

  public void testLocal2() { doTest(); }

  public void testSimpl1() { doTest(); }

  public void testSimpl2() { doTest(); }

  public void testSimpl3() { doTest(); }

  public void testWhile1() { doTest(); }

  public void doTest() {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    String text = data.get(0);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text);

    int selStart = myFixture.getEditor().getSelectionModel().getSelectionStart();
    int selEnd = myFixture.getEditor().getSelectionModel().getSelectionEnd();

    final GroovyFile file = (GroovyFile)myFixture.getFile();
    final PsiElement start = file.findElementAt(selStart);
    final PsiElement end = file.findElementAt(selEnd - 1);
    final GrControlFlowOwner owner = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(start, end), GrControlFlowOwner.class, false);
    assertNotNull(owner);
    GrStatement firstStatement = getStatement(start, owner);
    GrStatement lastStatement = getStatement(end, owner);

    final GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(firstStatement);
    final GroovyControlFlow flow = ControlFlowBuilder.buildControlFlow(flowOwner, GrAllVarsInitializedPolicy.getInstance());
    final FragmentVariableInfos fragmentVariableInfos =
      ReachingDefinitionsCollector.obtainVariableFlowInformation(firstStatement, lastStatement, flowOwner, flow);

    TestCase.assertEquals(data.get(1), dumpInfo(fragmentVariableInfos).trim());
  }

  private static String dumpInfo(FragmentVariableInfos fragmentVariableInfos) {
    StringBuilder builder = new StringBuilder();
    builder.append("input:\n");
    for (VariableInfo info : fragmentVariableInfos.getInputVariableNames()) {
      builder.append(info.getName()).append("\n");
    }

    builder.append("output:\n");
    for (VariableInfo info : fragmentVariableInfos.getOutputVariableNames()) {
      builder.append(info.getName()).append("\n");
    }

    return builder.toString();
  }

  private static GrStatement getStatement(@NotNull PsiElement element, PsiElement context) {
    while (!element.getParent().equals(context)) {
      element = element.getParent();
      assertNotNull(element);
    }

    return (GrStatement)element;
  }

  @Override
  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  private String basePath = TestUtils.getTestDataPath() + "groovy/reachingDefs/";
}
