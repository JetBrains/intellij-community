// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal.jar;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.jar.JarTaskManifestConfiguration;

import java.util.Map;

public class JarTaskManifestConfigurationImpl implements JarTaskManifestConfiguration {
  private final @NotNull Map<String, String> manifestAttributes;

  public JarTaskManifestConfigurationImpl(@NotNull Map<String, String> manifestAttributes) {
    this.manifestAttributes = manifestAttributes;
  }

  @Override
  public @NotNull Map<String, String> getManifestAttributes() {
    return manifestAttributes;
  }
}
