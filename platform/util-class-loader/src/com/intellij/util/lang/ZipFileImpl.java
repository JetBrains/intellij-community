// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.util.lang.fastutil.StrippedIntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;

interface ZipFileImpl extends Closeable {
  @Nullable JarMemoryLoader preload(@NotNull Path basePath, @Nullable JarLoader attributesProvider) throws IOException;

  @Nullable Attributes loadManifestAttributes() throws IOException;

  @NotNull ClasspathCache.LoaderData buildClassPathCacheData() throws IOException;

  @NotNull StrippedIntOpenHashSet buildPackageHashes() throws IOException;

  @Nullable Resource getResource(@NotNull String name, @NotNull JarLoader jarLoader) throws IOException;
}
