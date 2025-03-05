// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.util.Objects;

@ApiStatus.Internal
public final class InternalProjectIdentifier implements ProjectIdentifier {
  private final InternalBuildIdentifier build;
  private final String projectPath;

  public InternalProjectIdentifier(InternalBuildIdentifier build, String projectPath) {
    this.build = build;
    this.projectPath = projectPath;
  }

  public InternalProjectIdentifier(File rootDir, String projectPath) {
    this(new InternalBuildIdentifier(rootDir), projectPath);
  }

  @Override
  public InternalBuildIdentifier getBuildIdentifier() {
    return this.build;
  }

  @Override
  public String getProjectPath() {
    return this.projectPath;
  }

  public File getRootDir() {
    return this.build.getRootDir();
  }

  @Override
  public String toString() {
    return String.format("project=%s, %s", projectPath, build);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InternalProjectIdentifier that = (InternalProjectIdentifier)o;
    if (!Objects.equals(build, that.build)) return false;
    if (!Objects.equals(projectPath, that.projectPath)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = build != null ? build.hashCode() : 0;
    result = 31 * result + (projectPath != null ? projectPath.hashCode() : 0);
    return result;
  }
}
