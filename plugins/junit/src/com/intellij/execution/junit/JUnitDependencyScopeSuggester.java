/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

public class JUnitDependencyScopeSuggester extends LibraryDependencyScopeSuggester {
  private static final String[] JUNIT_JAR_MARKERS = {
    "org.junit.Test", "junit.framework.TestCase", "org.hamcrest.Matcher", "org.hamcrest.Matchers",
    "org.junit.jupiter.api.Test", "org.junit.platform.commons.JUnitException", "org.opentest4j.AssertionFailedError"
  };

  @Nullable
  @Override
  public DependencyScope getDefaultDependencyScope(@NotNull Library library) {
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
