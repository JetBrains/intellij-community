// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.packaging.jpackage;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class JPackageArtifactPropertiesProvider extends ArtifactPropertiesProvider {

  protected JPackageArtifactPropertiesProvider() {
    super("jpackage-properties");
  }
  @Override
  public @NotNull ArtifactProperties<?> createProperties(@NotNull ArtifactType artifactType) {
    return new JPackageArtifactProperties();
  }

  @Override
  public boolean isAvailableFor(@NotNull ArtifactType type) {
    return type instanceof JPackageArtifactType;
  }
}
