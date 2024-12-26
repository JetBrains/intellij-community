// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl.artifacts;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

public final class AntArtifactPostprocessingPropertiesProvider extends ArtifactPropertiesProvider {
  public static AntArtifactPostprocessingPropertiesProvider getInstance() {
    return EP_NAME.findExtension(AntArtifactPostprocessingPropertiesProvider.class);
  }

  private AntArtifactPostprocessingPropertiesProvider() {
    super("ant-postprocessing");
  }

  @Override
  public @NotNull ArtifactProperties<?> createProperties(@NotNull ArtifactType artifactType) {
    return new AntArtifactProperties(true);
  }

}
