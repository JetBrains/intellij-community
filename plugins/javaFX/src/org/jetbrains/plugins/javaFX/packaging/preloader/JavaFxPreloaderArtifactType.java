// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.packaging.preloader;

import com.intellij.icons.AllIcons;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFXBundle;

import javax.swing.*;

public final class JavaFxPreloaderArtifactType extends ArtifactType {
  public static JavaFxPreloaderArtifactType getInstance() {
    return EP_NAME.findExtension(JavaFxPreloaderArtifactType.class);
  }
  
  private JavaFxPreloaderArtifactType() {
    super("javafx-preloader", JavaFXBundle.messagePointer("javafx.preloader.title"));
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Nodes.Artifact;
  }

  @Override
  public @Nullable String getDefaultPathFor(@NotNull PackagingElementOutputKind kind) {
    return "/";
  }

  @Override
  public @NotNull CompositePackagingElement<?> createRootElement(@NotNull String artifactName) {
    return PackagingElementFactory.getInstance().createArchive(artifactName + ".jar");
  }
}
