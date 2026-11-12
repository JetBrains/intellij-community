// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixture;
import org.jetbrains.plugins.groovy.util.ThrowingTransformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;

@TestApplication
public class GrNoTransformationsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    ThrowingTransformation.disableTransformations(myFixture.getTestRootDisposable());
    addSomeClasses();
  }

  @Test
  public void testAnnotationResolveStaticImport() {
    resolve.get().resolveByText("""
                    import static foo.bar.Hello.Foo
                    @Fo<caret>o
                    def foo() {}
                    """, null);
  }

  @Test
  public void testAnnotationResolveStaticStarImport() {
    resolve.get().resolveByText("""
                    import static foo.bar.World.*
                    @Fo<caret>o
                    def foo() {}
                    """, null);
  }

  @Test
  public void testResolveLocalInScript() {
    resolve.get().resolveByText("""
                    import static foo.bar.Hello.*
                    def bar = 1
                    <caret>bar
                    """, GrVariable.class);
  }

  @Test
  public void testResolveLocalFromMethodInScript() {
    resolve.get().resolveByText("""
                    import static foo.bar.Hello.*
                    
                    def bar = 42
                    
                    def foo() {
                      def bar = 1
                      <caret>bar
                    }\s
                    """, GrVariable.class);
  }

  @Test
  public void testResolveParameterFromMethodInScript() {
    resolve.get().resolveByText("""
                    import static foo.bar.World.*
                    
                    def bar = 42
                    
                    def foo(bar) {
                      <caret>bar
                    }
                    """, GrParameter.class);
  }

  @Test
  public void testResolveParameterFromMethodInClass() {
    resolve.get().resolveByText("""
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

  @Test
  public void testResolveLocalFromInsideAnonymous() {
    resolve.get().resolveByText("""
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

  @Test
  public void testResolveParameterFromInsideAnonymous() {
    resolve.get().resolveByText("""
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
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("foo/bar/classes.groovy", """
      package foo.bar
      class Hello {}
      class World {}
      """);
  }
}
