// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.jar.Attributes;

public interface ResourceFile {
  @Nullable Attributes loadManifestAttributes() throws IOException;

  @NotNull ClasspathCache.IndexRegistrar buildClassPathCacheData() throws IOException;

  @Nullable Resource getResource(@NotNull String name, @NotNull JarLoader jarLoader) throws IOException;

  @Nullable Class<?> findClass(String fileName, String className, JarLoader jarLoader, ClassPath.ClassDataConsumer classConsumer)
    throws IOException;

  void processResources(@NotNull String dir,
                        @NotNull Predicate<? super String> nameFilter,
                        @NotNull BiConsumer<? super String, ? super InputStream> consumer) throws IOException;
}
