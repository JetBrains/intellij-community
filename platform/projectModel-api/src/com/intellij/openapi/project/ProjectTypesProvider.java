// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * <p>Detects project types in a project based on project roots information such as libraries and SDKs.
 * Inspections and actions can be marked with a project type to not instantiate them in irrelevant projects.
 * </p>
 * <p>Usage example: Android global inspections must not run in projects without Android SDK.</p>
 * <p>
 * Use it only in case of significant performance problems caused by plugin actions or inspections running in irrelevant projects.
 * </p>
 *
 * @see ProjectTypeService
 */
@ApiStatus.Experimental
public interface ProjectTypesProvider {
  ExtensionPointName<ProjectTypesProvider> EP_NAME = ExtensionPointName.create("com.intellij.projectTypesProvider");

  /**
   * @return found project types
   */
  @NotNull @Unmodifiable
  Collection<ProjectType> inferProjectTypes(@NotNull Project project);
}
