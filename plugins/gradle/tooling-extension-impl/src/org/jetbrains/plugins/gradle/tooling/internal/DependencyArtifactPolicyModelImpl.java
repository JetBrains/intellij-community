// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal;

import org.jetbrains.plugins.gradle.model.internal.DependencyArtifactPolicyModel;

import java.io.Serializable;

public class DependencyArtifactPolicyModelImpl implements DependencyArtifactPolicyModel, Serializable {

  private final boolean downloadSources;

  private final boolean downloadJavadoc;

  public DependencyArtifactPolicyModelImpl(boolean downloadSources, boolean downloadJavadoc) {
    this.downloadSources = downloadSources;
    this.downloadJavadoc = downloadJavadoc;
  }

  @Override
  public boolean isDownloadSources() {
    return downloadSources;
  }

  @Override
  public boolean isDownloadJavadoc() {
    return downloadJavadoc;
  }
}
