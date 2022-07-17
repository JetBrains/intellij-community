// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang.java6;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;

/**
 * An object responsible for loading classes and resources from a particular classpath element: a jar or a directory.
 *
 * @see JarLoader
 * @see FileLoader
 */
abstract class Loader {
  @NotNull
  private final URL myURL;
  private ClasspathCache.NameFilter myLoadingFilter;

  Loader(@NotNull URL url) {
    myURL = url;
  }

  @NotNull
  URL getBaseURL() {
    return myURL;
  }

  @Nullable
  abstract Resource getResource(@NotNull String name);

  @NotNull
  abstract ClasspathCache.LoaderData buildData() throws IOException;

  boolean containsName(@NotNull String name, @NotNull String shortName) {
    if (name.isEmpty()) {
      return true;
    }
    ClasspathCache.NameFilter filter = myLoadingFilter;
    return filter == null || filter.maybeContains(shortName);
  }

  void applyData(@NotNull ClasspathCache.LoaderData loaderData) {
    myLoadingFilter = loaderData.getNameFilter();
  }
}
