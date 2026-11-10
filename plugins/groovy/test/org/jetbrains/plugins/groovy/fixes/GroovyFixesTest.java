// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyConstantIfStatementInspection;
import org.jetbrains.plugins.groovy.codeInspection.style.JavaStylePropertiesInvocationInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class GroovyFixesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testRemoveUnnecessaryModifier() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("gr.groovy", """
      var <caret>var integers = [1, 2, 3]
      """);
    myFixture.launchAction(myFixture.findSingleIntention("Remove 'var' modifier"));
    myFixture.checkResult("""
                            var integers = [1, 2, 3]
                            """);
  }

  @Test
  public void testSuppressForIfStatement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GroovyConstantIfStatementInspection());
    myFixture.configureByText("a.groovy", """
      <caret>if (true) {
        aaa
      }""");
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for statement"));
    myFixture.checkResult("""
                            //noinspection GroovyConstantIfStatement
                            if (true) {
                              aaa
                            }""");
  }

  @Test
  public void testShallowChangeToGroovyStylePropertyAccess() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new JavaStylePropertiesInvocationInspection());
    myFixture.configureByText("a.groovy", """
      class GroovyClasss {
        def initializer
        def foo() {
          setInitializer({
            <caret>println "hello"
          })
        }
      }
      
      """);
    assertThat(myFixture.filterAvailableIntentions("Change to Groovy-style property reference")).isNullOrEmpty();
  }

  @Test
  public void testSecondAnnotationSuppression() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GrUnresolvedAccessInspection());
    myFixture.configureByText("a.groovy", """
      class FooBarGoo {
        @SuppressWarnings(["GroovyParameterNamingConvention"])
        def test(Object abc) {
          abc.d<caret>ef()
        }
      }
      """);
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for method"));
    myFixture.checkResult("""
                            class FooBarGoo {
                              @SuppressWarnings(["GroovyParameterNamingConvention", 'GrUnresolvedAccess'])
                              def test(Object abc) {
                                abc.def()
                              }
                            }
                            """);
  }

  @Test
  public void testSecondAnnotationSuppression2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GrUnresolvedAccessInspection());
    myFixture.configureByText("a.groovy", """
      class FooBarGoo {
        @SuppressWarnings("GroovyParameterNamingConvention")
        def test(Object abc) {
          abc.d<caret>ef()
        }
      }
      """);
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for method"));
    myFixture.checkResult("""
                            class FooBarGoo {
                              @SuppressWarnings(["GroovyParameterNamingConvention", 'GrUnresolvedAccess'])
                              def test(Object abc) {
                                abc.def()
                              }
                            }
                            """);
  }

  @Test
  public void testFixPackageName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.groovy", """
      #!/usr/bin/groovy
      
      class Foo {}
      """);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      ((GroovyFile)myFixture.getFile()).setPackageName("foo");
    });
    myFixture.checkResult("""
                            #!/usr/bin/groovy
                            package foo
                            
                            class Foo {}
                            """);
  }
}
