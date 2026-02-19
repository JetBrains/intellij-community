// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.packaging.preloader;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

public final class JavaFxPreloaderArtifactPropertiesProvider extends ArtifactPropertiesProvider {
  private JavaFxPreloaderArtifactPropertiesProvider() {
    super("javafx-preloader-properties");
  }

  @Override
  public boolean isAvailableFor(@NotNull ArtifactType type) {
    return type instanceof JavaFxPreloaderArtifactType;
  }

  @Override
  public @NotNull ArtifactProperties<?> createProperties(@NotNull ArtifactType artifactType) {
    return new JavaFxPreloaderArtifactProperties();
  }
  
  public static JavaFxPreloaderArtifactPropertiesProvider getInstance() {
    return EP_NAME.findExtension(JavaFxPreloaderArtifactPropertiesProvider.class);
  }
}
