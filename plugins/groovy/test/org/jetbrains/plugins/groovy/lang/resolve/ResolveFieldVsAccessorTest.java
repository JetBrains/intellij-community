// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.ThrowingTransformation;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveFieldVsAccessorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @Test
  public void testImplicitThis() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    resolve.get().resolveByText("""
                    class A {
                      def prop = "field"
                      def getProp() { "getter" }
                    
                      def implicitThis() {
                        <caret>prop
                      }
                    """, GrField.class);
  }

  @Test
  public void testExplicitThis() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    resolve.get().resolveByText("""
                    class A {
                      def prop = "field"
                      def getProp() { "getter" }
                    
                      def explicitThis() {
                        this.<caret>prop
                      }
                    }
                    """, GrField.class);
  }

  @Test
  public void testQualified() {
    GrMethod method = resolve.get().resolveByText("""
                                      class A {
                                        def prop = "field"
                                        def getProp() { "getter" }
                                      
                                        def qualifiedUsage() {
                                          new A().<caret>prop
                                        }
                                      }
                                      """, GrMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("getProp", method.getName());
    });
  }

  @Test
  public void testInnerClass() {
    resolve.get().resolveByText("""
                    class A {
                      def prop = "field"
                      def getProp() { "getter" }
                    
                      def innerClass() {
                        new Runnable() {
                          void run() {
                            <caret>prop
                          }
                        }
                      }
                    }
                    """, GrMethod.class);
  }

  @Test
  public void testInnerClassExplicitThis() {
    resolve.get().resolveByText("""
                    class A {
                      def prop = "field"
                      def getProp() { "getter" }
                    
                      def innerExplicitThis = new Runnable() {
                        void run() {
                          println A.this.<caret>prop
                        }
                      }
                    }
                    """, GrMethod.class);
  }

  @Test
  public void testInnerVsOuter() {
    GrMethod method = resolve.get().resolveByText(
      """
        class A {
          def prop = "field"
          def getProp() { "getter" }
        
          def innerProperty = new Runnable() {
            def getProp() { "inner getter" }
        
            void run() {
              println <caret>prop
            }
          }
        }
        """, GrMethod.class);
    ReadAction.runBlocking(() -> {
      assertTrue(method.getContainingClass() instanceof GrAnonymousClassDefinition);
    });
  }

  @Test
  public void testImplicitSuper() {
    resolve.get().resolveByText("""
                    class A {
                      def prop = "field"
                      def getProp() { "getter" }
                    }
                    
                    class B extends A {
                      def implicitSuper() {
                        <caret>prop
                      }
                    }
                    """, GrMethod.class);
  }

  @Test
  public void testExplicitSuper() {
    resolve.get().resolveByText("""
                    class A {
                      def prop = "field"
                      def getProp() { "getter" }
                    }
                    
                    class B extends A {
                      def explicitSuper() {
                        super.<caret>prop
                      }
                    }
                    """, GrMethod.class);
  }
}
