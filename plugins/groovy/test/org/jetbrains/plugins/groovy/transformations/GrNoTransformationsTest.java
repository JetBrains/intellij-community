// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase;
import org.jetbrains.plugins.groovy.util.ThrowingTransformation;

public class GrNoTransformationsTest extends GroovyResolveTestCase {
  @Override
  @NotNull
  public LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ThrowingTransformation.disableTransformations(getTestRootDisposable());
    addSomeClasses();
  }

  public void testAnnotationResolveStaticImport() {
    resolveByText("""
                    import static foo.bar.Hello.Foo
                    @Fo<caret>o
                    def foo() {}
                    """, null);
  }

  public void testAnnotationResolveStaticStarImport() {
    resolveByText("""
                    import static foo.bar.World.*
                    @Fo<caret>o
                    def foo() {}
                    """, null);
  }

  public void testResolveLocalInScript() {
    resolveByText("""
                    import static foo.bar.Hello.*
                    def bar = 1
                    <caret>bar
                    """, GrVariable.class);
  }

  public void testResolveLocalFromMethodInScript() {
    resolveByText("""
                    import static foo.bar.Hello.*
                    
                    def bar = 42
                    
                    def foo() {
                      def bar = 1
                      <caret>bar
                    }\s
                    """, GrVariable.class);
  }

  public void testResolveParameterFromMethodInScript() {
    resolveByText("""
                    import static foo.bar.World.*
                    
                    def bar = 42
                    
                    def foo(bar) {
                      <caret>bar
                    }
                    """, GrParameter.class);
  }

  public void testResolveParameterFromMethodInClass() {
    resolveByText("""
                    import static foo.bar.World.*
                    
                    def bar = 42
                    
                    class M {
                      def bar
                      def foo(bar) {
                        <caret>bar
                      }
                    }
                    """, GrParameter.class);
  }

  public void testResolveLocalFromInsideAnonymous() {
    resolveByText("""
                    import static foo.bar.Hello.*
                    
                    def foo() {
                      def bar = 1
                      new Runnable() {
                        void run() {
                          <caret>bar
                        }
                      }
                    }
                    """, GrVariable.class);
  }

  public void testResolveParameterFromInsideAnonymous() {
    resolveByText("""
                    import static foo.bar.Hello.*
                    
                    def foo(bar) {
                      new Runnable() {
                        void run() {
                          <caret>bar
                        }
                      }
                    }
                    """, GrParameter.class);
  }

  private void addSomeClasses() {
    myFixture.addFileToProject("foo/bar/classes.groovy", """
      package foo.bar
      class Hello {}
      class World {}
      """);
  }
}
