package org.jetbrains.plugins.groovy;

import com.intellij.openapi.paths.WebReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.InjectionTestFixtureKt;
import org.jetbrains.annotations.NotNull;

public class GroovyWebReferenceTest extends LightGroovyTestCase {
  public void test_web_reference_in_strings() {
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

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
