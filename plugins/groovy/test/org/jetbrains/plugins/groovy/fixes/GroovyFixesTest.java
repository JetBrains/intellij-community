// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyConstantIfStatementInspection;
import org.jetbrains.plugins.groovy.codeInspection.style.JavaStylePropertiesInvocationInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

public class GroovyFixesTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSuppressForIfStatement() {
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

  public void testShallowChangeToGroovyStylePropertyAccess() {
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
    UsefulTestCase.assertEmpty(myFixture.filterAvailableIntentions("Change to Groovy-style property reference"));
  }

  public void testSecondAnnotationSuppression() {
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

  public void testSecondAnnotationSuppression2() {
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

  public void testFixPackageName() {
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
