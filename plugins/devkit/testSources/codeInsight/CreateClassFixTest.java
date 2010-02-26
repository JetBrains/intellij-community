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

/*
 * User: anna
 * Date: 14-Jun-2007
 */
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.idea.devkit.inspections.RegistrationProblemsInspection;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

public class CreateClassFixTest {
  protected CodeInsightTestFixture myFixture;

  @BeforeMethod
  public void setUp() throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder();
    myFixture = fixtureFactory.createCodeInsightFixture(testFixtureBuilder.getFixture());
    myFixture.setTestDataPath(PluginPathManager.getPluginHomePath("devkit") + "/testData");

    testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class)
      .addContentRoot(myFixture.getTempDirPath()).addSourceRoot(getSourceRoot());
    myFixture.enableInspections(new RegistrationProblemsInspection());
    myFixture.setUp();
  }

  @AfterMethod
  public void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
  }

  private static String getSourceRoot() {
    return "codeInsight";
  }

  @DataProvider
  public Object[][] data() {
    return new Object[][]{{"Action", true}, {"Impl", true}, {"Intf", true}, {"Intf", false}};
  }


  @Test(dataProvider = "data", enabled = false)
  public void test(String testName, boolean createClass) throws Throwable {
    IntentionAction resultAction = null;
    final String createAction = QuickFixBundle.message(createClass ? "create.class.text" : "create.interface.text", testName);
    final List<IntentionAction> actions = myFixture.getAvailableIntentions(getSourceRoot() + "/plugin" + testName + ".xml");
    for (IntentionAction action : actions) {
      if (Comparing.strEqual(action.getText(), createAction)) {
        resultAction = action;
        break;
      }
    }
    Assert.assertNotNull(resultAction);
    myFixture.launchAction(resultAction);
    final Project project = myFixture.getProject();
    Assert.assertNotNull(JavaPsiFacade.getInstance(project).findClass(testName, GlobalSearchScope.allScope(project)));
  }
}
