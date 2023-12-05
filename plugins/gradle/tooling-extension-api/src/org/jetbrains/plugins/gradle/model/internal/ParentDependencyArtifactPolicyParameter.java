// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.internal;

import org.gradle.api.Action;

public interface ParentDependencyArtifactPolicyParameter {
  boolean isDownloadSources();

  void setDownloadSources(boolean downloadSources);

  boolean isDownloadJavadoc();

  void setDownloadJavadoc(boolean downloadJavadoc);

  class Initializer implements Action<ParentDependencyArtifactPolicyParameter> {

    private final DependencyArtifactPolicyModel model;

    public Initializer(DependencyArtifactPolicyModel model) {
      this.model = model;
    }

    @Override
    public void execute(ParentDependencyArtifactPolicyParameter parameter) {
      parameter.setDownloadSources(model.isDownloadSources());
      parameter.setDownloadJavadoc(model.isDownloadJavadoc());
    }
  }
}
