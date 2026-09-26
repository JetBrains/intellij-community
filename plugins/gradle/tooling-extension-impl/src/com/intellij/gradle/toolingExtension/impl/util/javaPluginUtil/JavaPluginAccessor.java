// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil;

import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.Nullable;

public interface JavaPluginAccessor {
  @Nullable
  SourceSetContainer getSourceSetContainer();

  @Nullable
  String getTargetCompatibility();

  @Nullable
  String getSourceCompatibility();

  boolean isJavaPluginApplied();
}
