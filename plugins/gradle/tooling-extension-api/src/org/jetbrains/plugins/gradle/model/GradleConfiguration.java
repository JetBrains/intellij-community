// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public interface GradleConfiguration extends Serializable {
  @NotNull
  String getName();

  @Nullable
  String getDescription();

  boolean isVisible();

  boolean isScriptClasspathConfiguration();

  @NotNull List<String> getDeclarationAlternatives();
}
