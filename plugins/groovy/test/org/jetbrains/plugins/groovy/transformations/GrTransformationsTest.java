// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GrTransformationsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @Test
  public void testSkipCompiledClassesWhileCheckingIfToIncludeSyntheticMembers() {
    GrAccessorMethod resolved = resolve.get().resolveByText("""
                                          
                                              class MyBase {}
                                              trait MyT {}
                                              class MyInheritor extends Script implements MyT {
                                                def ppp
                                              }
                                              new MyInheritor().pp<caret>p
                                              """, GrAccessorMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("getPpp", resolved.getName());
    });
  }

  @Test
  public void testTransformAnonymousClasses() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("Base.groovy", """
        abstract class Base {
          abstract getFoo()
        }
        """);
    myFixture.configureByText("a.groovy", """
        class A {
          def baz = new Base() { // no error, getFoo() exists
            def foo = 1
          }
        }
        """);
    myFixture.checkHighlighting();
  }
}
