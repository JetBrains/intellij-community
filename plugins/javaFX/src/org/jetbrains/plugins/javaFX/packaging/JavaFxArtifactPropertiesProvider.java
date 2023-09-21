// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

public final class JavaFxArtifactPropertiesProvider extends ArtifactPropertiesProvider {
  private JavaFxArtifactPropertiesProvider() {
    super("javafx-properties");
  }

  @Override
  public boolean isAvailableFor(@NotNull ArtifactType type) {
    return type instanceof JavaFxApplicationArtifactType;
  }

  @Override
  public @NotNull ArtifactProperties<?> createProperties(@NotNull ArtifactType artifactType) {
    return new JavaFxArtifactProperties();
  }
  
  public static JavaFxArtifactPropertiesProvider getInstance() {
    return EP_NAME.findExtension(JavaFxArtifactPropertiesProvider.class);
  }
}
