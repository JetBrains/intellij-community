// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryDependencyScopeSuggester;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;

public final class JUnitDependencyScopeSuggester extends LibraryDependencyScopeSuggester {
  private static final String[] JUNIT_JAR_MARKERS = {
    "org.junit.Test", "junit.framework.TestCase", "org.hamcrest.Matcher", "org.hamcrest.Matchers",
    "org.junit.jupiter.api.Test", "org.junit.platform.commons.JUnitException", "org.opentest4j.AssertionFailedError"
  };

  @Override
  public @Nullable DependencyScope getDefaultDependencyScope(@NotNull Library library) {
    VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    long testJars = Arrays.stream(files).filter(JUnitDependencyScopeSuggester::isTestJarRoot).count();
    long regularJars = files.length - testJars;
    return testJars > regularJars ? DependencyScope.TEST : null;
  }

  private static boolean isTestJarRoot(VirtualFile file) {
    for (String marker : JUNIT_JAR_MARKERS) {
      if (LibraryUtil.isClassAvailableInLibrary(Collections.singletonList(file), marker)) {
        return true;
      }
    }
    return false;
  }
}
