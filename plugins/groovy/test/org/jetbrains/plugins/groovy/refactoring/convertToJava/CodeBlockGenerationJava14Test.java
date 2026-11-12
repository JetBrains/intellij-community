package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class CodeBlockGenerationJava14Test {
  // Java 14 has no text blocks.
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_14);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, CodeBlockGenerationTestUtil.TEST_DATA_PATH);

  @Test
  public void testMultilineStringOldJava() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CodeBlockGenerationTestUtil.doTest(myFixture);
  }
}
