// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class JarMemoryLoader {
  /** Special entry to keep the number of reordered classes in jar. */
  public static final String SIZE_ENTRY = "META-INF/jb/$$size$$";

  private final Map<String, Resource> resources;

  JarMemoryLoader(@NotNull Map<String, Resource> resources) {
    this.resources = resources;
  }

  public synchronized Resource getResource(@NotNull String entryName) {
    return resources.remove(entryName);
  }
}
