/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 27, 2006
 * Time: 7:49:54 PM
 */
package com.intellij.lang.ant;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import junit.framework.TestCase;

/**
 * @author Maxim.Mossienko
 */
public class AntMultiFileCompletionTest extends TestCase {
  private CodeInsightTestFixture myFixture;

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/ant/tests/data/psi/completion/";
  }

  public void testPropertyCompletion() throws Throwable {
    final String testName = "PropertyCompletion";
    doTestFor(
      new String [] { testName, testName + ".properties" },
      "xml"
    );
  }

  public void testPropertyCompletion2() throws Throwable {
    final String testName = "PropertyCompletion2";
    doTestFor(
      new String [] { testName, "PropertyCompletion.properties" },
      "xml"
    );
  }

  private void doTestFor(final String[] fileNames, final String ext) throws Throwable {

    String initialTest = fileNames[0];
    for(int i = 0; i < fileNames.length; ++i) {
      if (fileNames[i].indexOf('.') == -1) fileNames[i] += "."+ext;
    }

    myFixture.testCompletion(fileNames, initialTest + "_after." + ext);
  }

  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder();
    myFixture = fixtureFactory.createCodeInsightFixture(testFixtureBuilder.getFixture());
    myFixture.setTestDataPath(getTestDataPath());
    myFixture.setUp();
  }

  protected void tearDown() throws Exception {
    myFixture.tearDown();
    super.tearDown();
  }
}