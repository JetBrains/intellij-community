// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * An object responsible for loading classes and resources from a particular classpath element: a jar or a directory.
 *
 * @see JarLoader
 * @see FileLoader
 */
abstract class Loader {
  final @NotNull Path path;
  private ClasspathCache.NameFilter loadingFilter;

  Loader(@NotNull Path path) {
    this.path = path;
  }

  abstract @Nullable Resource getResource(@NotNull String name);

  abstract @NotNull ClasspathCache.LoaderData buildData() throws IOException;

  final boolean containsName(@NotNull String name, @NotNull String shortName) {
    if (name.isEmpty()) {
      return true;
    }
    ClasspathCache.NameFilter filter = loadingFilter;
    return filter == null || filter.maybeContains(shortName);
  }

  final void applyData(@NotNull ClasspathCache.LoaderData loaderData) {
    loadingFilter = loaderData.getNameFilter();
  }
}
