package org.jetbrains.plugins.groovy.intentions.style.parameterToEntry;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import junit.framework.Assert;
import org.jetbrains.plugins.grails.fileType.GspFileType;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.*;

/**
 * @author ilyas
 */
public class ParameterToMapEntryTest extends UsefulTestCase {

  protected CodeInsightTestFixture myFixture;
  protected Project myProject;
  protected CodeStyleSettings mySettings;


  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> builder = fixtureFactory.createFixtureBuilder();
    myFixture = fixtureFactory.createCodeInsightFixture(builder.getFixture());
    final JavaModuleFixtureBuilder moduleBuilder = builder.addModule(JavaModuleFixtureBuilder.class);
    moduleBuilder.addJdk(TestUtils.getMockJdkHome());
    myFixture.setTestDataPath(TestUtils.getTestDataPath() + "/paramToMap" + "/" + getTestName(true));
    moduleBuilder.addContentRoot(myFixture.getTempDirPath()).addSourceRoot("");
    myFixture.setUp();

    myProject = myFixture.getProject();
    setSettings();
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    super.tearDown();
  }

  public void testParam1() throws Throwable {
    doTestImpl("A.groovy");
  }

  public void testClosureAtEnd() throws Throwable {
    doTestImpl("A.groovy");
  }

  public void testClosure1() throws Throwable {
    doTestImpl("A.groovy");
  }

  public void testNewMap() throws Throwable {
    doTestImpl("A.groovy");
  }

  public void testTestError() throws Throwable {
    doTestImpl("A.groovy");
  }

  public void testSecondClosure() throws Throwable {
    doTestImpl("A.groovy");
  }

  private void doTestImpl(String filePath) throws Throwable {
    myFixture.configureByFile(filePath);
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    final PsiFile file = myFixture.getFile();


    final ConvertParameterToMapEntryIntention intention = new ConvertParameterToMapEntryIntention();
    PsiElement element = file.findElementAt(offset);
    while (element != null && !(element instanceof GrReferenceExpression || element instanceof GrParameter)) {
      element = element.getParent();
    }
    Assert.assertNotNull(element);

    final PsiElementPredicate condition = intention.getElementPredicate();
    Assert.assertTrue(condition.satisfiedBy(element));

    // Launch it!
    intention.processIntention(element);
    final String result = file.getText();
    System.out.println(result);
    String expected = getExpectedResult(filePath);
    Assert.assertEquals(result, expected);
  }

  private String getExpectedResult(final String filePath) {
    Assert.assertTrue(filePath.endsWith(".groovy"));
    String testFilePath = StringUtil.trimEnd(filePath, "groovy") + "test";

    final File file = new File(TestUtils.getTestDataPath() + "/paramToMap" + "/" + getTestName(true) + "/" + testFilePath);
    assertTrue(file.exists());
    String expected = "";

    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      String line = reader.readLine();
      while (line != null) {
        expected += line;
        line = reader.readLine();
        if (line != null) expected += "\n";
      }
      reader.close();
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    finally {

      return expected;
    }
  }

  protected CodeStyleSettings getSettings() {
    return CodeStyleSettingsManager.getSettings(myProject);
  }

  protected final void setSettings() {
    mySettings = getSettings();
    mySettings.getIndentOptions(GroovyFileType.GROOVY_FILE_TYPE).INDENT_SIZE = 2;
    mySettings.getIndentOptions(GroovyFileType.GROOVY_FILE_TYPE).CONTINUATION_INDENT_SIZE = 4;
    mySettings.getIndentOptions(GroovyFileType.GROOVY_FILE_TYPE).TAB_SIZE = 2;
    mySettings.getIndentOptions(GspFileType.GSP_FILE_TYPE).INDENT_SIZE = 2;
    mySettings.getIndentOptions(GspFileType.GSP_FILE_TYPE).CONTINUATION_INDENT_SIZE = 4;
    mySettings.getIndentOptions(GspFileType.GSP_FILE_TYPE).TAB_SIZE = 2;
  }

}