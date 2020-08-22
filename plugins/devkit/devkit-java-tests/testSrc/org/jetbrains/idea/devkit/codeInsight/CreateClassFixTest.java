// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.inspections.RegistrationProblemsInspection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class CreateClassFixTest extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;

  @org.junit.runners.Parameterized.Parameter(0) public String myTestName;
  @org.junit.runners.Parameterized.Parameter(1) public boolean myCreateClass;

  @Before
  public void before() throws Exception {
    JavaTestFixtureFactory fixtureFactory = JavaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = JavaTestFixtureFactory.createFixtureBuilder(getClass().getSimpleName());
    myFixture = fixtureFactory.createCodeInsightFixture(testFixtureBuilder.getFixture());
    myFixture.setTestDataPath(DevkitJavaTestsUtil.TESTDATA_ABSOLUTE_PATH);

    testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class).addContentRoot(myFixture.getTempDirPath()).addSourceRoot(getSourceRoot());
    myFixture.setUp();
    myFixture.enableInspections(new RegistrationProblemsInspection());
  }

  @After
  public void after() throws Exception {
    myFixture.tearDown();
    myFixture = null;
  }

  @NotNull
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
  public void runSingle() {
    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction resultAction = null;
      final String createAction = CommonQuickFixBundle.message(
        "fix.create.title.x", (myCreateClass ? JavaElementKind.CLASS : JavaElementKind.INTERFACE).object(), myTestName);
      final List<IntentionAction> actions = myFixture.getAvailableIntentions(getSourceRoot() + "/plugin" + myTestName + ".xml");
      for (IntentionAction action : actions) {
        if (Comparing.strEqual(action.getText(), createAction)) {
          resultAction = action;
          break;
        }
      }
      org.junit.Assert.assertNotNull(resultAction);
      myFixture.launchAction(resultAction);
      final Project project = myFixture.getProject();
      org.junit.Assert.assertNotNull(JavaPsiFacade.getInstance(project).findClass(myTestName, GlobalSearchScope.allScope(project)));
    });
  }
}
