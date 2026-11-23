// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.indexing;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.TestIndexingModeSupporter.EmptyIndexSuite;
import com.intellij.testFramework.TestIndexingModeSupporter.FullIndexSuite;
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode;
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingModeTestHandler;
import com.intellij.testFramework.TestIndexingModeSupporter.RuntimeOnlyIndexSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContext;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContextProvider;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Gives the runs of an {@link IndexingModeTemplate} class.
 */
@TestOnly
public final class IndexingModeTemplateProvider implements ClassTemplateInvocationContextProvider {
  /**
   * The configuration parameter that selects the modes: {@code smart} (the default), {@code dumb} or {@code all}.
   * A {@code @Suite} sets it with {@code @ConfigurationParameter}. A local run can set it as a system property.
   */
  public static final String MODES_PARAMETER = "intellij.test.indexing.modes";

  /**
   * The configuration parameter that limits a {@code @Suite} to one TeamCity build, as a JUnit 3 indexing mode suite does.
   * Its value is a build name. When the system property {@code teamcity.buildConfName} has another value, the classes do not run.
   * The system property {@link #MODES_PARAMETER} removes the limit for a local run.
   */
  public static final String BUILD_PARAMETER = "intellij.test.indexing.modes.build";

  private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(IndexingModeTemplateProvider.class);

  /**
   * Returns the mode of the current run of an {@link IndexingModeTemplate} class, or {@link IndexingMode#SMART} outside such a run.
   */
  public static @NotNull IndexingMode getIndexingMode(@NotNull ExtensionContext context) {
    IndexingMode mode = context.getStore(NAMESPACE).get(IndexingMode.class, IndexingMode.class);
    return mode != null ? mode : IndexingMode.SMART;
  }

  @Override
  public boolean supportsClassTemplate(@NotNull ExtensionContext context) {
    return true;
  }

  @Override
  public @NotNull Stream<? extends ClassTemplateInvocationContext> provideClassTemplateInvocationContexts(@NotNull ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    String modes = context.getConfigurationParameter(MODES_PARAMETER).orElse("smart");
    if (!List.of("smart", "dumb", "all").contains(modes)) {
      throw new IllegalArgumentException("Unknown value of " + MODES_PARAMETER + ": " + modes);
    }
    String build = context.getConfigurationParameter(BUILD_PARAMETER).orElse(null);
    if (build != null && !build.equals(System.getProperty("teamcity.buildConfName")) && System.getProperty(MODES_PARAMETER) == null) {
      return Stream.empty();
    }
    List<ClassTemplateInvocationContext> contexts = new ArrayList<>();
    if (!modes.equals("dumb")) {
      contexts.add(new IndexingModeInvocationContext(IndexingMode.SMART, null));
    }
    if (!modes.equals("smart")) {
      List<IndexingModeTestHandler> handlers = new ArrayList<>(List.of(new FullIndexSuite(), new RuntimeOnlyIndexSuite()));
      if (Registry.is("ide.dumb.mode.check.awareness")) {
        handlers.add(new EmptyIndexSuite());
      }
      for (IndexingModeTestHandler handler : handlers) {
        if (!handler.shouldIgnore(testClass)) {
          contexts.add(new IndexingModeInvocationContext(handler.getIndexingMode(), handler));
        }
      }
    }
    return contexts.stream();
  }

  @Override
  public boolean mayReturnZeroClassTemplateInvocationContexts(@NotNull ExtensionContext context) {
    // A class with NeedsIndex.SmartMode has no run in the dumb modes, and a suite of another build has no run at all.
    return true;
  }

  private record IndexingModeInvocationContext(@NotNull IndexingMode mode, @Nullable IndexingModeTestHandler handler)
    implements ClassTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
      return "[" + mode.name() + "]";
    }

    @Override
    public void prepareInvocation(@NotNull ExtensionContext context) {
      context.getStore(NAMESPACE).put(IndexingMode.class, mode);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
      return List.of((ExecutionCondition)context -> {
        Method method = context.getTestMethod().orElse(null);
        if (method != null && handler != null && handler.shouldIgnore(method)) {
          return ConditionEvaluationResult.disabled("The test does not run in " + mode.name());
        }
        return ConditionEvaluationResult.enabled(null);
      });
    }
  }
}
