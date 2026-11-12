// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Medvedev Max
 */
@TestApplication
public class StaticCheckTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GroovyResolveFixture> resolve =
    groovyResolveFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "resolve/static/");

  protected void doTest(boolean staticOk) {
    final GroovyResolveResult resolveResult = resolve.get().advancedResolve("a.groovy");
    ReadAction.runBlocking(() -> {
      assertNotNull(resolveResult);
      final PsiElement element = resolveResult.getElement();
      assertNotNull(element);
      assertEquals(staticOk, resolveResult.isStaticsOK());
    });
  }

  @Test
  public void testPropInStaticInnerClass() {
    doTest(false);
  }

  @Test
  public void testThisInStaticInnerClass() {
    doTest(true);
  }

  @Test
  public void testWithInsideTheClass() {
    doTest(true);
  }
}
