// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class DefaultGradleDependencyDownloadPolicy implements GradleDependencyDownloadPolicy {

  private final boolean isDownloadSources;
  private final boolean isDownloadJavadoc;

  public DefaultGradleDependencyDownloadPolicy(boolean isDownloadSources, boolean isDownloadJavadoc) {
    this.isDownloadSources = isDownloadSources;
    this.isDownloadJavadoc = isDownloadJavadoc;
  }

  @Override
  public boolean isDownloadSources() {
    return isDownloadSources;
  }

  @Override
  public boolean isDownloadJavadoc() {
    return isDownloadJavadoc;
  }
}
