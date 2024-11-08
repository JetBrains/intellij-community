// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class SSBasedInspectionTest extends SSBasedInspectionTestCase {

  public void testExpressionStatement() {
    doTest("File.createTempFile($p1$, $p2$)", "Forbid File.createTempFile");
  }

  public void testTwoStatementPattern() {
    SearchConfiguration configuration = new SearchConfiguration();
    configuration.setName("silly null check");
    configuration.setSuppressId("SillyNullCheck");
    final MatchOptions matchOptions = configuration.getMatchOptions();
    matchOptions.setFileType(JavaFileType.INSTANCE);
    matchOptions.fillSearchCriteria("""
             $field$ = $something$;
             if ($field$ == null) {
                  throw new $Exception$($msg$);
             }""");
    inspectionTest(configuration, HighlightDisplayLevel.ERROR);
    quickFixTest("Suppress for statement");
  }

  public void testBrokenPattern() {
    // check broken pattern does not throw exceptions
    doTest("int i(", "semicolon expected");
  }

  public void testAnnotation() {
    doTest("@'Anno:[regex( Nullable|NotNull )] '_Type:[regex( .*(\\[\\])+ )] '_x;", "report annotation only once");
  }

  public void testElementOutsideOfFile() {
    doTest("class '_ { \n  '_ReturnType 'Method+:* ('_ParameterType '_Parameter*);\n}", "all methods of the class within hierarchy");
  }

  public void testDeclaration() {
    doTest("int i;", "int declaration");
  }

  public void testMethodCall() {
    doTest("f();", "method call");
  }

  public void testQuickFix() {
    doTest("System.out.println();", "stdout", "System.out.println(\"Hello World!\");");
  }

  public void testChainedMethodCallsPerformance() {
    final StringBuilder source = new StringBuilder("""
                                                     public class ChainedMethodCallsPerformance {

                                                       void x() {
                                                         new StringBuilder()
                                                     """);
    for (int i = 0; i < 400; i++) {
      source.append("      .append(").append(i).append(")\n");
    }
    source.append("  }\n" +
                  "}");
    myFixture.configureByText("ChainedMethodCallsPerformance.java", source.toString());

    final SearchConfiguration configuration = new SearchConfiguration("Chained method call", "test");
    final MatchOptions options = configuration.getMatchOptions();
    options.setFileType(JavaFileType.INSTANCE);
    options.fillSearchCriteria("'_x.'y('_z)");
    options.setRecursiveSearch(true);

    StructuralSearchProfileActionProvider.createNewInspection(configuration, myFixture.getProject());
    final InspectionProfileImpl profile = InspectionProfileManager.getInstance(myFixture.getProject()).getCurrentProfile();
    final ToolsImpl tools = profile.getToolsOrNull("SSBasedInspection", myFixture.getProject());
    final SSBasedInspection inspection = (SSBasedInspection)tools.getTool().getTool();
    final PsiFile file = myFixture.getFile();
    Benchmark.newBenchmark("Chained method call inspection performance",
                           () -> InspectionEngine.inspectEx(
                                            Collections.singletonList(new LocalInspectionToolWrapper(inspection)), file,
                                            file.getTextRange(),
                                            file.getTextRange(), true, false, true, new DaemonProgressIndicator(), PairProcessor.alwaysTrue())).start();
  }

  public void doTest(String pattern, String name) {
    doTest(JavaFileType.INSTANCE, pattern, name);
  }

  public void doTest(String pattern, String name, String replacement) {
    doTest(JavaFileType.INSTANCE, pattern, name, replacement);
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/ssBased";
  }

  @Override
  @NotNull
  protected String getExtension() {
    return ".java";
  }
}
