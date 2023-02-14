// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.packaging.preloader;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaFxPreloaderArtifactProperties extends ArtifactProperties<JavaFxPreloaderArtifactProperties> {

  private String myPreloaderClass;

  @Override
  public ArtifactPropertiesEditor createEditor(@NotNull ArtifactEditorContext context) {
    return new JavaFxPreloaderArtifactPropertiesEditor(this, context.getProject(), context.getArtifact());
  }

  @Nullable
  @Override
  public JavaFxPreloaderArtifactProperties getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JavaFxPreloaderArtifactProperties state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public String getPreloaderClass() {
    return myPreloaderClass;
  }

  public void setPreloaderClass(String preloaderClass) {
    myPreloaderClass = preloaderClass;
  }
}
