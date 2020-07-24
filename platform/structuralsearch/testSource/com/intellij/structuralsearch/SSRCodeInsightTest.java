// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.structuralsearch.inspection.SSBasedInspection;
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;

public class SSRCodeInsightTest extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;
  private SSBasedInspection myInspection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(new DefaultLightProjectDescriptor());
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
    doTest("$field$ = $something$;\n" +
           "if ($field$ == null) {\n" +
           "     throw new $Exception$($msg$);\n" +
           "}",
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

  private void doTest(final String searchPattern, final String patternName) {
    final SearchConfiguration configuration = new SearchConfiguration();
    //display name
    configuration.setName(patternName);

    final MatchOptions options = configuration.getMatchOptions();
    options.setFileType(JavaFileType.INSTANCE);
    options.fillSearchCriteria(searchPattern);

    StructuralSearchProfileActionProvider.createNewInspection(configuration, myFixture.getProject());
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/ssBased";
  }
}
