// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.ThrowingTransformation;
import org.junit.Assert;

public class ResolveFieldVsAccessorTest extends GroovyResolveTestCase {

  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  public void testImplicitThis() {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    resolveByText("""
                    class A {
                      def prop = "field"
                      def getProp() { "getter" }
                    
                      def implicitThis() {
                        <caret>prop
                      }
                    """, GrField.class);
  }

  public void testExplicitThis() {
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    resolveByText("""
                    class A {
                      def prop = "field"
                      def getProp() { "getter" }
                    
                      def explicitThis() {
                        this.<caret>prop
                      }
                    }
                    """, GrField.class);
  }

  public void testQualified() {
    GrMethod method = resolveByText("""
                                      class A {
                                        def prop = "field"
                                        def getProp() { "getter" }
                                      
                                        def qualifiedUsage() {
                                          new A().<caret>prop
                                        }
                                      }
                                      """, GrMethod.class);
    Assert.assertEquals("getProp", method.getName());
  }

  public void testInnerClass() {
    resolveByText("""
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

  public void testInnerClassExplicitThis() {
    resolveByText("""
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

  public void testInnerVsOuter() {
    GrMethod method = resolveByText(
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
    Assert.assertTrue(method.getContainingClass() instanceof GrAnonymousClassDefinition);
  }

  public void testImplicitSuper() {
    resolveByText("""
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

  public void testExplicitSuper() {
    resolveByText("""
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
