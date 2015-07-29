/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.idea.devkit.inspections.RegistrationProblemsInspection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testng.Assert;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class CreateClassFixTest extends UsefulTestCase{
  protected CodeInsightTestFixture myFixture;

  @org.junit.runners.Parameterized.Parameter(0) public String myTestName;
  @org.junit.runners.Parameterized.Parameter(1) public boolean myCreateClass;

  @Before
  public void setUp() throws Exception {
    final Ref<Exception> ex = new Ref<Exception>();
    Runnable runnable = new Runnable() {
      public void run() {
        try {
          CreateClassFixTest.super.setUp();
          final JavaTestFixtureFactory fixtureFactory = JavaTestFixtureFactory.getFixtureFactory();
          final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = JavaTestFixtureFactory.createFixtureBuilder(getClass().getSimpleName());
          myFixture = fixtureFactory.createCodeInsightFixture(testFixtureBuilder.getFixture());
          myFixture.setTestDataPath(PluginPathManager.getPluginHomePath("devkit") + "/testData");

          testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class)
            .addContentRoot(myFixture.getTempDirPath()).addSourceRoot(getSourceRoot());
          myFixture.setUp();
          myFixture.enableInspections(new RegistrationProblemsInspection());
        }
        catch (Exception e) {
          ex.set(e);
        }
      }
    };
    invokeTestRunnable(runnable);
    final Exception exception = ex.get();
    if (exception != null) {
      throw exception;
    }
  }

  @After
  public void tearDown() throws Exception {
    final Ref<Exception> ex = new Ref<Exception>();
    Runnable runnable = new Runnable() {
      public void run() {
        try {
          myFixture.tearDown();
          myFixture = null;
          CreateClassFixTest.super.tearDown();
        }
        catch (Exception e) {
          ex.set(e);
        }
      }
    };
    invokeTestRunnable(runnable);
    final Exception exception = ex.get();
    if (exception != null) {
      throw exception;
    }
  }

  private static String getSourceRoot() {
    return "codeInsight";
  }

  @Parameterized.Parameters(name = "{0} : {1}")
  public static List<Object[]> data() {
    return Arrays.asList(new Object[]{"Action", true}, 
                         new Object[]{"Impl", true}, 
                         new Object[]{"Intf", true}, 
                         new Object[]{"Intf", false});
  }


  @Test
  public void runSingle() throws Throwable {
    Runnable runnable = new Runnable() {
      public void run() {
        IntentionAction resultAction = null;
        final String createAction = QuickFixBundle.message(myCreateClass ? "create.class.text" : "create.interface.text", myTestName);
        final List<IntentionAction> actions = myFixture.getAvailableIntentions(getSourceRoot() + "/plugin" + myTestName + ".xml");
        for (IntentionAction action : actions) {
          if (Comparing.strEqual(action.getText(), createAction)) {
            resultAction = action;
            break;
          }
        }
        Assert.assertNotNull(resultAction);
        myFixture.launchAction(resultAction);
        final Project project = myFixture.getProject();
        Assert.assertNotNull(JavaPsiFacade.getInstance(project).findClass(myTestName, GlobalSearchScope.allScope(project)));
      }
    };
    invokeTestRunnable(runnable);
  }
}
