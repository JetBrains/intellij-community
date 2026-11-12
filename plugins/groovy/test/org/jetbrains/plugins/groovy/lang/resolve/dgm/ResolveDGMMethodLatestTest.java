// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.dgm;

import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ResolveDGMMethodLatestTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @Test
  public void testCloseable() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addClass("package java.io; class Closeable {}");
    GrGdkMethod resolved = resolve.get().resolveByText(
      """
        class A implements Closeable {}
        new A().withC<caret>loseable {}
        """, GrGdkMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("org.codehaus.groovy.runtime.IOGroovyMethods",
                          resolved.getStaticMethod().getContainingClass().getQualifiedName());
    });
  }
}
