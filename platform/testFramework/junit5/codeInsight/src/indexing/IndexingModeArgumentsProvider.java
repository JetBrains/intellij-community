// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.indexing;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.TestIndexingModeSupporter;
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.ParameterDeclarations;

import java.lang.reflect.AnnotatedElement;
import java.util.stream.Stream;

/**
 * Provides the {@link IndexingMode} values for a JUnit 5 parameterized test.
 * It skips the same modes as {@link TestIndexingModeSupporter#addAllTests} does for a JUnit 3 test,
 * according to the {@link com.intellij.testFramework.NeedsIndex} annotations on the test method and the test class.
 * {@link IndexingModes} on the test class sets the modes explicitly.
 * <p>
 * The test runs its body in the mode. It wraps the fixture with {@code IndexingModeCodeInsightTestFixture.wrapFixture},
 * calls {@link IndexingMode#setUpTest} before the body adds files, and calls {@link IndexingMode#tearDownTest} in a {@code finally}.
 */
@TestOnly
public final class IndexingModeArgumentsProvider implements ArgumentsProvider {
  @Override
  public @NotNull Stream<? extends Arguments> provideArguments(@NotNull ParameterDeclarations parameters, @NotNull ExtensionContext context) {
    AnnotatedElement method = context.getRequiredTestMethod();
    AnnotatedElement testClass = context.getRequiredTestClass();
    IndexingModes explicitModes = testClass.getAnnotation(IndexingModes.class);
    if (explicitModes != null) {
      return Stream.of(explicitModes.value()).map(Arguments::of);
    }
    return Stream.of(IndexingMode.values())
      .filter(mode -> !shouldIgnore(mode, testClass) && !shouldIgnore(mode, method))
      .map(Arguments::of);
  }

  private static boolean shouldIgnore(IndexingMode mode, AnnotatedElement element) {
    return switch (mode) {
      case SMART -> false;
      case DUMB_FULL_INDEX -> new TestIndexingModeSupporter.FullIndexSuite().shouldIgnore(element);
      case DUMB_RUNTIME_ONLY_INDEX -> new TestIndexingModeSupporter.RuntimeOnlyIndexSuite().shouldIgnore(element);
      case DUMB_EMPTY_INDEX -> !Registry.is("ide.dumb.mode.check.awareness") ||
                               new TestIndexingModeSupporter.EmptyIndexSuite().shouldIgnore(element);
    };
  }
}
