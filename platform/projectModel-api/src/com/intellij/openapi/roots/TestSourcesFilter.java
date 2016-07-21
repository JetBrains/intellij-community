/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Implementations of this extension point can tell IDE whether some particular file is a test file.
 * <p>
 * By default, IntelliJ Platform considers files as tests only if they are located under test
 * sources root {@link FileIndex#isInTestSourceContent(VirtualFile)}.
 * <p>
 * However there are plenty frameworks and languages which keep test files just nearby production files.
 * E.g. *_test.go files are test files in Go language and some js/dart files are test files depending
 * on their content. The extensions allow IDE to highlight such files with a green background,
 * properly check if they are included in built-in search scopes, etc.
 *
 * @see FileIndex#isInTestSourceContent(VirtualFile)
 * @since 2016.3
 * @author zolotov
 */
public abstract class TestSourcesFilter {
  public static final ExtensionPointName<TestSourcesFilter> EP_NAME = ExtensionPointName.create("com.intellij.testSourcesFilter");

  public static boolean isTestSources(@NotNull VirtualFile file, @NotNull Project project) {
    for (TestSourcesFilter filter : EP_NAME.getExtensions()) {
      if (filter.isTestSource(file, project)) {
        return true;
      }
    }
    return false;
  }

  public abstract boolean isTestSource(@NotNull VirtualFile file, @NotNull Project project);
}
