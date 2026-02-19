// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.util.HighlightingTest;

public abstract class GroovyVersionBasedTest extends LightGroovyTestCase implements HighlightingTest {
  public void testIdentityOperators() { fileHighlightingTest(); }

  public void testElvisAssignment() { fileHighlightingTest(); }

  public void testSafeIndexAccess() { fileHighlightingTest(); }

  public void testNegatedIn() { fileHighlightingTest(); }

  public void testNegatedInstanceof() { fileHighlightingTest(); }

  public void testMethodReference() { fileHighlightingTest(); }

  public void testDoWhile() { fileHighlightingTest(); }

  public void testFor() { fileHighlightingTest(); }

  public void testTryResources() { fileHighlightingTest(); }

  public void testArrayInitializers() { fileHighlightingTest(); }

  public void testLambdas() { fileHighlightingTest(); }

  public void testAmbiguousCodeBlock() { fileHighlightingTest(); }

  public void testTypeAnnotations() { fileHighlightingTest(); }

  public void testApplicationTupleInitializer() { fileHighlightingTest(); }

  public void testTupleMultipleAssignment() { fileHighlightingTest(); }
}
