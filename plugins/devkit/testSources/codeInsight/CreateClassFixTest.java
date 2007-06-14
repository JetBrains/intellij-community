/*
 * User: anna
 * Date: 14-Jun-2007
 */
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.idea.devkit.inspections.RegistrationProblemsInspection;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import java.util.List;

public class CreateClassFixTest {
  protected CodeInsightTestFixture myFixture;

  @BeforeMethod
  public void setUp() throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder();
    myFixture = fixtureFactory.createCodeInsightFixture(testFixtureBuilder.getFixture());
    myFixture.setTestDataPath(PathManager.getHomePath() + "/plugins/devkit/testData");

    testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class)
      .addContentRoot(myFixture.getTempDirPath()).addSourceRoot(getSourceRoot());
    myFixture.enableInspections(new RegistrationProblemsInspection());
    myFixture.setUp();
  }

  private String getSourceRoot() {
    return "codeInsight";
  }

  @DataProvider
  public Object[][] data() {
    return new Object[][]{{"Action", true}, {"Impl", true}, {"Intf", true}, {"Intf", false}};
  }

  @Test(dataProvider = "data")
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
    Assert.assertNotNull(PsiManager.getInstance(myFixture.getProject()).findClass(testName));
  }


  @AfterMethod
  public void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
  }
}