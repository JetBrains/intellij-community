// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests.bazel.bucketing;

import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BucketsPostDiscoveryFilter implements PostDiscoveryFilter {
  private static final MethodHandle included;
  static {
    try {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      included = MethodHandles.publicLookup()
        .findStatic(Class.forName("com.intellij.TestCaseLoader", true, classLoader),
                    "isClassIncluded", MethodType.methodType(boolean.class, String.class));
    }
    catch (NoSuchMethodException | ClassNotFoundException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private final List<String> includedClasses = new ArrayList<>();
  private final List<String> excludedClasses = new ArrayList<>();

  public Boolean hasExcludedClasses() {
    return !excludedClasses.isEmpty();
  }

  public Boolean hasIncludedClasses() {
    return !includedClasses.isEmpty();
  }

  @Override
  public FilterResult apply(TestDescriptor descriptor) {
    if (descriptor instanceof EngineDescriptor) {
      return FilterResult.included(null);
    }
    TestSource source = descriptor.getSource().orElse(null);
    if (source == null) {
      return FilterResult.included("No source for descriptor");
    }
    if (source instanceof MethodSource methodSource) {
      return isIncluded(methodSource.getClassName());
    }
    if (source instanceof ClassSource classSource) {
      return isIncluded(classSource.getClassName());
    }
    return FilterResult.included("Unknown source type " + source.getClass());
  }

  // Cache results per class to avoid redundant reflective calls and incorrect "last only" caching behavior
  private final Map<String, FilterResult> resultsCache = new HashMap<>();

  private FilterResult isIncluded(String className) {
    FilterResult result = resultsCache.get(className);
    if (result == null) {
      result = isIncludedImpl(className);
      resultsCache.put(className, result);
      if (result.included()) {
        includedClasses.add(className);
      }
      else {
        excludedClasses.add(className);
      }
    }
    return result;
  }

  private FilterResult isIncludedImpl(String className) {
    try {
      if ((boolean)included.invokeExact(className)) {
        return FilterResult.included(null);
      }
      return FilterResult.excluded(null);
    }
    catch (Throwable e) {
      String message = e.getClass().getSimpleName() + (e.getMessage() != null ? (": " + e.getMessage()) : "");
      return FilterResult.excluded(message);
    }
  }
}
