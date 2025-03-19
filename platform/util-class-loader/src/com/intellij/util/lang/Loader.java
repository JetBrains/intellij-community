// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * An object responsible for loading classes and resources from a particular classpath element: a jar or a directory.
 */
@ApiStatus.Internal
public interface Loader {
  Path getPath();

  @Nullable Resource getResource(String name);

  void processResources(String dir,
                        Predicate<? super String> fileNameFilter,
                        BiConsumer<? super String, ? super InputStream> consumer) throws IOException;

  @Nullable Class<?> findClass(String fileName, String className, ClassPath.ClassDataConsumer classConsumer) throws IOException;
}
