// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

/**
 * Controls where project configuration files for projects imported from an external build system (Maven, Gradle, etc) are stored. By default,
 * the project configuration is stored under .idea directory located in the base project directory. If the external storage is enabled,
 * parts of the project configuration which are imported from the external build system are stored under
 * {@link com.intellij.openapi.application.PathManager#getSystemPath the cache directory}. This way these generated files don't pollute the project directories,
 * users won't put them into version control system, and won't get changed files in their working copies after changing build scripts and
 * reimporting the project. If user adds a module or a library via Project Structure, or adds a facet to an imported module, their configuration
 * will be stored under .idea anyway, so such manually configured parts won't be lost if the cache directory is cleared.
 */
public interface ExternalStorageConfigurationManager {
  static ExternalStorageConfigurationManager getInstance(Project project) {
    return project.getService(ExternalStorageConfigurationManager.class);
  }

  boolean isEnabled();

  /**
   * Instruct the system to store project configuration files in external storage (if {@code value} is {@code true}) or under the project
   * base directory (if {@code value} is {@code false}). By default, the external storage is disabled.
   */
  void setEnabled(boolean value);
}
