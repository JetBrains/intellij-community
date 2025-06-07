// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

@ApiStatus.Internal
public final class ClassPathStorageUtil {
  public static final @NonNls String DEFAULT_STORAGE = "default";

  public static @NotNull @NonNls String getStorageType(@NotNull Module module) {
    String id = module.getOptionValue(JpsProjectLoader.CLASSPATH_ATTRIBUTE);
    return id == null ? DEFAULT_STORAGE : id;
  }

  public static boolean isClasspathStorage(@NotNull Module module) {
    return module.getOptionValue(JpsProjectLoader.CLASSPATH_ATTRIBUTE) != null;
  }
}
