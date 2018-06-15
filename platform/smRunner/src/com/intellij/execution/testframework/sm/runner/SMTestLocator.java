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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.Location;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A parser for location URLs reported by test runners.
 * See {@link SMTestProxy#getLocation(Project, GlobalSearchScope)} for details.
 */
public interface SMTestLocator {
  /**
   * Creates the <code>Location</code> list from <code>protocol</code> and <code>path</code> in <code>scope</code>.
   */
   @NotNull
  List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope);

  /**
   * Creates the <code>Location</code> list from <code>protocol</code>, <code>path</code>, and <code>metainfo</code> in <code>scope</code>.
   * Implementation of test framework can provide additional information in <code>metainfo</code> parameter,
   * The <code>metainfo</code> parameter simplifies the search for locations, but can not be used to identify the test.
   * A good example for code>metainfo</code> is the line number of the beginning of the test. It can speed up the search procedure,
   * but it changes when editing.
   */
  @NotNull
  default List<Location> getLocation(@NotNull String protocol, @NotNull String path, @Nullable String metainfo, @NotNull Project project,
                                     @NotNull GlobalSearchScope scope) {
    return getLocation(protocol, path, project, scope);
  }

  /**
   * Parse stacktrace line and return corresponding location.
   */
  @NotNull
  default List<Location> getLocation(@NotNull String stacktraceLine,
                                     @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return Collections.emptyList();
  }
}