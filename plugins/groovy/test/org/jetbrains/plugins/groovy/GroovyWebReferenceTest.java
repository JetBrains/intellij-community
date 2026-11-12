package org.jetbrains.plugins.groovy;

import com.intellij.openapi.paths.WebReference;
import com.intellij.testFramework.fixtures.InjectionTestFixtureKt;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GroovyWebReferenceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void test_web_reference_in_strings() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Demo.groovy", """
         class Demo {
           static void main(String[] args) {
             def doubleQuotes = "http://double:8080/app"
             def singleQuotes = 'http://single:8080/app'
             
             def multilineSingle = '''
                 https://multiline-single:8080/app
             '''
             
             def multilineDouble = ""\"
                 http://multiline-double:8080/app
             ""\"
           }
         }
      """);

    InjectionTestFixtureKt.assertInjectedReference(myFixture, WebReference.class, "http://double:8080/app");
    InjectionTestFixtureKt.assertInjectedReference(myFixture, WebReference.class, "http://single:8080/app");
    InjectionTestFixtureKt.assertInjectedReference(myFixture, WebReference.class, "https://multiline-single:8080/app");
    InjectionTestFixtureKt.assertInjectedReference(myFixture, WebReference.class, "http://multiline-double:8080/app");
  }
}
