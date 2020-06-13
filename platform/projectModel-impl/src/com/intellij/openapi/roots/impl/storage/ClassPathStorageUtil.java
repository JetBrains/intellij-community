// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

public final class ClassPathStorageUtil {
  @NonNls public static final String DEFAULT_STORAGE = "default";

  @NotNull
  public static String getStorageType(@NotNull Module module) {
    String id = module.getOptionValue(JpsProjectLoader.CLASSPATH_ATTRIBUTE);
    return id == null ? DEFAULT_STORAGE : id;
  }

  public static boolean isClasspathStorage(@NotNull Module module) {
    return module.getOptionValue(JpsProjectLoader.CLASSPATH_ATTRIBUTE) != null;
  }
}
