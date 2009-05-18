package com.intellij.lang.ant.config.impl;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class AntArtifactPostprocessingPropertiesProvider extends ArtifactPropertiesProvider {
  protected AntArtifactPostprocessingPropertiesProvider() {
    super("ant-postprocessing");
  }

  @NotNull
  public ArtifactProperties<?> createProperties(@NotNull ArtifactType artifactType) {
    return new AntArtifactPostprocessingProperties();
  }

}
