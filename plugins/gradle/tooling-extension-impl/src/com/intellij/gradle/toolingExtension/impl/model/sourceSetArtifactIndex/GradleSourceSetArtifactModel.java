// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetArtifactIndex;

import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public class GradleSourceSetArtifactModel {

  private @NotNull Map<String, SourceSet> mySourceSetArtifactMap;
  private @NotNull Map<String, String> mySourceSetOutputArtifactMap;

  public GradleSourceSetArtifactModel() {
    mySourceSetArtifactMap = new LinkedHashMap<>();
    mySourceSetOutputArtifactMap = new LinkedHashMap<>();
  }

  public @NotNull Map<String, SourceSet> getSourceSetArtifactMap() {
    return mySourceSetArtifactMap;
  }

  public void setSourceSetArtifactMap(@NotNull Map<String, SourceSet> sourceSetArtifactMap) {
    mySourceSetArtifactMap = sourceSetArtifactMap;
  }

  public @NotNull Map<String, String> getSourceSetOutputArtifactMap() {
    return mySourceSetOutputArtifactMap;
  }

  public void setSourceSetOutputArtifactMap(@NotNull Map<String, String> sourceSetOutputArtifactMap) {
    mySourceSetOutputArtifactMap = sourceSetOutputArtifactMap;
  }
}

