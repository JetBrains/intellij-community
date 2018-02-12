package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.inspection.highlightTemplate.SSBasedInspection;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;

import java.util.Collections;

public class SSRCodeInsightTest extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;
  private SSBasedInspection myInspection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(new DefaultLightProjectDescriptor());
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

  private void doTest(final String searchPattern, final String patternName) {
    final SearchConfiguration configuration = new SearchConfiguration();
    //display name
    configuration.setName(patternName);

    final MatchOptions options = configuration.getMatchOptions();
    options.setFileType(StdFileTypes.JAVA);
    options.setSearchPattern(searchPattern);

    myInspection.setConfigurations(Collections.singletonList(configuration), myFixture.getProject());
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/ssBased";
  }
}
