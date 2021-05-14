// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * An object responsible for loading classes and resources from a particular classpath element: a jar or a directory.
 *
 * @see JarLoader
 * @see FileLoader
 */
public abstract class Loader {
  public enum Attribute {
    SPEC_TITLE, SPEC_VERSION, SPEC_VENDOR, CLASS_PATH, IMPL_TITLE, IMPL_VERSION, IMPL_VENDOR
  }

  final @NotNull Path path;
  private Predicate<String> nameFilter;

  Loader(@NotNull Path path) {
    this.path = path;
  }

  abstract @Nullable Resource getResource(@NotNull String name);

  abstract void processResources(@NotNull String dir,
                                 @NotNull Predicate<? super String> fileNameFilter,
                                 @NotNull BiConsumer<? super String, ? super InputStream> consumer) throws IOException;

  public abstract Map<Loader.Attribute, String> getAttributes() throws IOException;

  abstract @Nullable Class<?> findClass(@NotNull String fileName, String className, ClassPath.ClassDataConsumer classConsumer) throws IOException;

  abstract @NotNull ClasspathCache.IndexRegistrar buildData() throws IOException;

  final boolean containsName(@NotNull String name) {
    if (name.isEmpty()) {
      return true;
    }

    Predicate<String> filter = nameFilter;
    return filter == null || filter.test(name);
  }

  final void setNameFilter(@NotNull Predicate<String> filter) {
    nameFilter = filter;
  }
}
