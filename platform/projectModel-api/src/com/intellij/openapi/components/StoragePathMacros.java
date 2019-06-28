// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * We have a framework for persisting component states (see {@link State} {@link Storage}). It allows to specify which file
 * should hold persisting data. There is a number of standard file system anchors like 'workspace file', 'project config dir' which
 * can be used for defining a storage file's path. Hence, IJ provides special support for such anchors in the form of macros,
 * i.e. special markers that are mapped to the current file system environment at runtime.
 * <p/>
 * This class holds those markers and utility method for working with them.
 */
public final class StoragePathMacros {
  /**
   * {@link Project#getWorkspaceFile() Workspace} file key.
   * {@code 'Workspace file'} holds settings that are local to a particular environment and should not be shared with another
   * team members.
   */
  @NotNull
  public static final String WORKSPACE_FILE = "$WORKSPACE_FILE$";

  /**
   * Storage file for cache-like data. Stored outside of project directory (if project level component)
   * and outside of application configuration directory (if application level component).
   */
  public static final String CACHE_FILE = "$CACHE_FILE$";

  /**
   * Applicable only for project-level.
   */
  @ApiStatus.Experimental
  @NotNull
  public static final String PRODUCT_WORKSPACE_FILE = "$PRODUCT_WORKSPACE_FILE$";

  @NotNull
  public static final String MODULE_FILE = "$MODULE_FILE$";

  /**
   * Application level non-roamable storage.
   */
  public static final String NON_ROAMABLE_FILE = PathManager.DEFAULT_OPTIONS_FILE;

  private StoragePathMacros() {
  }
}
