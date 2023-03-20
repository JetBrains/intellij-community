// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.util.PairProcessor;

import java.util.Collections;

public class SSBasedInspectionTest extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;
  private SSBasedInspection myInspection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
      factory.createLightFixtureBuilder(new DefaultLightProjectDescriptor(), getTestName(false));
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
    myInspection = new SSBasedInspection();
    myFixture.setUp();
    myFixture.enableInspections(myInspection);
    myFixture.setTestDataPath(getTestDataPath());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      myInspection = null;
      super.tearDown();
    }
  }

  public void testExpressionStatement() {
    doTest("File.createTempFile($p1$, $p2$)", "Forbid File.createTempFile");
  }

  public void testTwoStatementPattern() {
    doTest("""
             $field$ = $something$;
             if ($field$ == null) {
                  throw new $Exception$($msg$);
             }""",
           "silly null check");
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
    PlatformTestUtil.startPerformanceTest("Chained method call inspection performance", 1500,
                                          () -> InspectionEngine.inspectEx(
                                            Collections.singletonList(new LocalInspectionToolWrapper(inspection)), file,
                                            file.getTextRange(),
                                            file.getTextRange(), true, false, true, new DaemonProgressIndicator(), PairProcessor.alwaysTrue())).assertTiming();
  }

  private void doTest(String searchPattern, String patternName) {
    doTest(searchPattern, patternName, null);
  }

  private void doTest(String search, String name, String replacement) {
    final Configuration configuration = replacement == null ? new SearchConfiguration() : new ReplaceConfiguration();
    configuration.setName(name);

    final MatchOptions matchOptions = configuration.getMatchOptions();
    matchOptions.setFileType(JavaFileType.INSTANCE);
    matchOptions.fillSearchCriteria(search);
    if (replacement != null) {
      configuration.getReplaceOptions().setReplacement(replacement);
    }

    StructuralSearchProfileActionProvider.createNewInspection(configuration, myFixture.getProject());
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
    if (replacement != null) {
      final IntentionAction intention = myFixture.getAvailableIntention(CommonQuickFixBundle.message("fix.replace.with.x", replacement));
      assertNotNull(intention);
      myFixture.checkPreviewAndLaunchAction(intention);
      myFixture.checkResultByFile(getTestName(false) + ".after.java");
    }
  }

  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/ssBased";
  }
}
