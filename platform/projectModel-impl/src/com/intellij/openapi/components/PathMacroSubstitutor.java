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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides methods to convert paths from absolute to portable form and vice versa.
 *
 * @see com.intellij.openapi.application.PathMacros
 */
public interface PathMacroSubstitutor {
  /**
   * Convert path to absolute by replacing all names of path variables by its values
   */
  @Contract("null -> null; !null -> !null")
  String expandPath(@Nullable String text);

  @Contract("null -> null; !null -> !null")
  default String collapsePath(@Nullable String text) {
    return collapsePath(text, false);
  }

  /**
   * Convert paths inside {@code text} to portable form by replacing all values of path variables by their names.
   * @param recursively if {@code true} all occurrences of paths inside {@code text} will be processed, otherwise {@code text} will be converted
   *                    only if its entire content is a path (or URL)
   */
  @Contract("null, _ -> null; !null, _ -> !null")
  String collapsePath(@Nullable String text, boolean recursively);

  /**
   * Process sub tags of {@code element} recursively and convert paths to absolute forms in all tag texts and attribute values.
   */
  void expandPaths(@NotNull Element element);

  default void collapsePaths(@NotNull Element element) {
    collapsePaths(element, false);
  }

  default void collapsePathsRecursively(@NotNull Element element) {
    collapsePaths(element, true);
  }

  /**
   * Process sub tags of {@code element} recursively and convert paths to portable forms in all tag texts and attribute values.
   * @param recursively if {@code true} all occurrences of paths inside tag texts and attribute values will be processed, otherwise
   * they will be converted only if their entire content is a path (or URL)
   */
  void collapsePaths(@NotNull Element element, boolean recursively);

  @Contract("null -> null; !null -> !null")
  default String collapsePathsRecursively(@Nullable String text) {
    return collapsePath(text, true);
  }
}
