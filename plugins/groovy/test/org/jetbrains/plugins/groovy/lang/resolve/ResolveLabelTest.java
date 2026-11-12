// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Maxim.Medvedev
 */

@TestApplication
public class ResolveLabelTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GroovyResolveFixture> resolve =
    groovyResolveFixture(projectFixture, TestUtils.getAbsoluteTestDataPath()+"resolve/label");

  @Test
  public void testLabelResolve() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    final PsiReference ref = resolve.get().configureByFile(myFixture.getTestName(true)+"/"+myFixture.getTestName(false) + ".groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertNotNull(resolved);
      assertInstanceOf(GrLabeledStatement.class, resolved);
    });
  }

  @Test
  public void testLabelResolve2() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    final PsiReference ref = resolve.get().configureByFile(myFixture.getTestName(true)+"/"+myFixture.getTestName(false) + ".groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertNotNull(resolved);
      assertInstanceOf(GrLabeledStatement.class, resolved);
    });
  }

  @Test
  public void testLabelNotResolved() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    final PsiReference ref = resolve.get().configureByFile(myFixture.getTestName(true)+"/"+myFixture.getTestName(false) + ".groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertNull(resolved);
    });
  }
}
