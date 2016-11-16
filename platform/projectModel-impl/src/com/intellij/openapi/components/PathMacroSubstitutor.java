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
package com.intellij.openapi.components;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public interface PathMacroSubstitutor {
  String expandPath(String path);

  @NotNull
  default String collapsePath(@NotNull String text) {
    return collapsePath(text, false);
  }

  String collapsePath(@NotNull String text, boolean recursively);

  void expandPaths(@NotNull Element element);

  /**
   * Path will be collapsed only if the entire content of an attribute (tag text) is a path, if a path is a substring of an attribute value it won't be collapsed.
   */
  default void collapsePaths(@NotNull Element element) {
    collapsePaths(element, false);
  }

  /**
   * Path will be collapsed even if a path is a substring of an attribute value.
   */
  default void collapsePathsRecursively(@NotNull Element element) {
    collapsePaths(element, true);
  }

  void collapsePaths(@NotNull Element element, boolean recursively);

  default String collapsePathsRecursively(@NotNull String string) {
    return collapsePath(string, true);
  }
}
