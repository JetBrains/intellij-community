/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.lang.ant;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

/**
 * @author Maxim.Mossienko
 */
public class AntMultiFileCompletionTest extends UsefulTestCase {
  private CodeInsightTestFixture myFixture;

  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/psi/completion/";
  }

  public void testPropertyCompletion() {
    final String testName = "PropertyCompletion";
    doTestFor(
      new String [] { testName, testName + ".properties" },
      "xml"
    );
  }

  public void testPropertyCompletion2() {
    final String testName = "PropertyCompletion2";
    final String[] fileNames = new String [] { testName + ".xml", "PropertyCompletion.properties" };
    myFixture.testCompletionTyping(fileNames, "\n", testName + "_after.xml");
  }

  private void doTestFor(final String[] fileNames, final String ext) {

    String initialTest = fileNames[0];
    for(int i = 0; i < fileNames.length; ++i) {
      if (fileNames[i].indexOf('.') == -1) fileNames[i] += "."+ext;
    }

    myFixture.testCompletion(fileNames, initialTest + "_after." + ext);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final JavaTestFixtureFactory fixtureFactory = JavaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createLightFixtureBuilder();
    myFixture = fixtureFactory.createCodeInsightFixture(testFixtureBuilder.getFixture());
    myFixture.setTestDataPath(getTestDataPath());
    myFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }
}
