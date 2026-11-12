// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

/**
 * The highlighting tests that each subclass runs with its own Groovy version and test data.
 */
public abstract class GroovyVersionBasedTest implements HighlightingTest {
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture;

  protected GroovyVersionBasedTest(TestFixture<LightJavaProject> projectFixture, String testDataPath) {
    codeInsightFixture = lightGroovyFixture(projectFixture, testDataPath);
  }

  @Override
  public @NotNull LightJavaCodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  @Override
  public String getTestName() {
    return LightGroovyFixtures.groovyTestName(codeInsightFixture.get());
  }

  @Test
  public void testIdentityOperators() { fileHighlightingTest(); }

  @Test
  public void testElvisAssignment() { fileHighlightingTest(); }

  @Test
  public void testSafeIndexAccess() { fileHighlightingTest(); }

  @Test
  public void testNegatedIn() { fileHighlightingTest(); }

  @Test
  public void testNegatedInstanceof() { fileHighlightingTest(); }

  @Test
  public void testMethodReference() { fileHighlightingTest(); }

  @Test
  public void testDoWhile() { fileHighlightingTest(); }

  @Test
  public void testFor() { fileHighlightingTest(); }

  @Test
  public void testTryResources() { fileHighlightingTest(); }

  @Test
  public void testArrayInitializers() { fileHighlightingTest(); }

  @Test
  public void testLambdas() { fileHighlightingTest(); }

  @Test
  public void testAmbiguousCodeBlock() { fileHighlightingTest(); }

  @Test
  public void testTypeAnnotations() { fileHighlightingTest(); }

  @Test
  public void testApplicationTupleInitializer() { fileHighlightingTest(); }

  @Test
  public void testTupleMultipleAssignment() { fileHighlightingTest(); }
}
