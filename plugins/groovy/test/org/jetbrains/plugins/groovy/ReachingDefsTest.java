// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ReachingDefsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/reachingDefs/");
  }

  @Test
  public void testAssign() { doTest(); }

  @Test
  public void testClosure() { doTest(); }

  @Test
  public void testClosure1() { doTest(); }

  @Test
  public void testEm1() { doTest(); }

  @Test
  public void testEm2() { doTest(); }

  @Test
  public void testEm3() { doTest(); }

  @Test
  public void testIf1() { doTest(); }

  @Test
  public void testInner() { doTest(); }

  @Test
  public void testLocal1() { doTest(); }

  @Test
  public void testLocal2() { doTest(); }

  @Test
  public void testSimpl1() { doTest(); }

  @Test
  public void testSimpl2() { doTest(); }

  @Test
  public void testSimpl3() { doTest(); }

  @Test
  public void testWhile1() { doTest(); }

  public void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test");
    String text = data.get(0);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text);

    ReadAction.runBlocking(() -> {
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

      assertEquals(data.get(1), dumpInfo(fragmentVariableInfos).trim());
    });
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
}
