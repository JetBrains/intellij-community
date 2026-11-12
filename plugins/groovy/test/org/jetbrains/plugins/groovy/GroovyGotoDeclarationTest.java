package org.jetbrains.plugins.groovy;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GroovyGotoDeclarationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "gotoDeclaration/");


  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = LightGroovyFixtures.groovyTestName(myFixture);
    myFixture.configureByFile(name + ".groovy");
    myFixture.performEditorAction("GotoDeclaration");
    myFixture.checkResultByFile(name + "_after.groovy");
  }

  @Test
  public void testDefaultConstructor() {
    doTest();
  }

  @Test
  public void testQualifierInNew() {
    doTest();
  }
}
