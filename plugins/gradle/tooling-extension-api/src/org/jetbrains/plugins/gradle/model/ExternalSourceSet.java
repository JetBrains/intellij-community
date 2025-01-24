// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public interface ExternalSourceSet extends Serializable {

  @NotNull
  String getName();

  boolean isPreview();

  @Nullable
  File getJavaToolchainHome();

  @Nullable
  String getSourceCompatibility();

  @Nullable
  String getTargetCompatibility();

  Collection<File> getArtifacts();

  Collection<ExternalDependency> getDependencies();

  @NotNull
  Map<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> getSources();
}
