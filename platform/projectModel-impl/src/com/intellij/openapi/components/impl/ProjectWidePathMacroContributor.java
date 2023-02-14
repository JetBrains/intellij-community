// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 *   The extension point allows providing project-wide path macros (contrary to the
 *   {@link com.intellij.openapi.application.PathMacroContributor} which is the application-wide)
 * </p>
 * <p>
 *   If you want your custom project-wide path macro to be available in the JPS then consider implementing separate Path Macro
 *   contributor for the JPS too {@link org.jetbrains.jps.model.serialization.JpsPathMacroContributor}
 * </p>
 */
@ApiStatus.Internal
public interface ProjectWidePathMacroContributor {
  ExtensionPointName<ProjectWidePathMacroContributor> EP_NAME = new ExtensionPointName<>("com.intellij.projectPathMacroContributor");

  /**
   * @param projectFilePath See {@link com.intellij.openapi.project.Project#getProjectFilePath}
   */
  @NotNull Map<@NotNull String, @NotNull String> getProjectPathMacros(@NotNull @SystemIndependent String projectFilePath);

  /**
   * @param projectFilePath See {@link com.intellij.openapi.project.Project#getProjectFilePath}
   */
  static @NotNull Map<@NotNull String, @NotNull String> getAllMacros(@NotNull @SystemIndependent String projectFilePath) {
    Map<String, String> result = new HashMap<>();
    for (ProjectWidePathMacroContributor contributor : EP_NAME.getExtensionList()) {
      result.putAll(contributor.getProjectPathMacros(projectFilePath));
    }
    return result;
  }
}
