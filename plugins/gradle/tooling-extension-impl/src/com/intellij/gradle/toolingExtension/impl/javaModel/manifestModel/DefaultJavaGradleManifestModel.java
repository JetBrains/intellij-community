// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.javaModel.manifestModel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.javaModel.JavaGradleManifestModel;

import java.util.Map;

public class DefaultJavaGradleManifestModel implements JavaGradleManifestModel {
  private final @NotNull Map<String, String> manifestAttributes;

  public DefaultJavaGradleManifestModel(@NotNull Map<String, String> manifestAttributes) {
    this.manifestAttributes = manifestAttributes;
  }

  @Override
  public @NotNull Map<String, String> getManifestAttributes() {
    return manifestAttributes;
  }
}
