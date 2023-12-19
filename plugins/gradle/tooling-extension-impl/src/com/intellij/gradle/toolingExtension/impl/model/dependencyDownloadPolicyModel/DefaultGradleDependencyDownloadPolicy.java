// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class DefaultGradleDependencyDownloadPolicy implements GradleDependencyDownloadPolicy {

  private boolean downloadSources;

  private boolean downloadJavadoc;

  public DefaultGradleDependencyDownloadPolicy() {
    downloadSources = false;
    downloadJavadoc = false;
  }

  @Override
  public boolean isDownloadSources() {
    return downloadSources;
  }

  public void setDownloadSources(boolean downloadSources) {
    this.downloadSources = downloadSources;
  }

  @Override
  public boolean isDownloadJavadoc() {
    return downloadJavadoc;
  }

  public void setDownloadJavadoc(boolean downloadJavadoc) {
    this.downloadJavadoc = downloadJavadoc;
  }
}
