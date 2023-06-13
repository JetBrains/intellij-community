// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.modelAction;

import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.Project;

import java.io.File;
import java.io.Serializable;

@ApiStatus.Internal
class DefaultProjectModel implements Project, Serializable {

  private final String myName;
  private final DefaultProjectIdentifier myProjectIdentifier;

  DefaultProjectModel(@NotNull String name, @NotNull File rootDir, @NotNull String projectPath) {
    myName = name;
    myProjectIdentifier = new DefaultProjectIdentifier(rootDir, projectPath);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public ProjectIdentifier getProjectIdentifier() {
    return myProjectIdentifier;
  }

  @Override
  public String toString() {
    return "ProjectModel{" +
           "name='" + myName + '\'' +
           ", id=" + myProjectIdentifier +
           '}';
  }
}
