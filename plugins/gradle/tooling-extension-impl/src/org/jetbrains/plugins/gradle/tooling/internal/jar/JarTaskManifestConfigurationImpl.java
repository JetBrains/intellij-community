// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal.jar;

import org.jetbrains.plugins.gradle.model.jar.JarTaskManifestConfiguration;

import java.util.Map;

public class JarTaskManifestConfigurationImpl implements JarTaskManifestConfiguration {
  private final Map<String, String> projectIdentityPathToModuleName;

  public JarTaskManifestConfigurationImpl(Map<String, String> projectIdentityPathToModuleName) {
    this.projectIdentityPathToModuleName = projectIdentityPathToModuleName;
  }

  @Override
  public Map<String, String> getProjectIdentityPathToModuleName() {
    return projectIdentityPathToModuleName;
  }
}
