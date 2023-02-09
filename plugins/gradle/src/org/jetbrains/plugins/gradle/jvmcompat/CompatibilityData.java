// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat;

import java.util.List;

public class CompatibilityData {
  public List<VersionMapping> versionMappings;
  public List<String> supportedJavaVersions;
  public List<String> supportedGradleVersions;

  public CompatibilityData() {
  }

  public CompatibilityData(List<VersionMapping> versionMappings,
                           List<String> supportedJavaVersions,
                           List<String> supportedGradleVersions) {
    this.versionMappings = versionMappings;
    this.supportedJavaVersions = supportedJavaVersions;
    this.supportedGradleVersions = supportedGradleVersions;
  }
}
