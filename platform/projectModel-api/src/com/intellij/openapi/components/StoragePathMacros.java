// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import org.jetbrains.annotations.ApiStatus.Internal;

/**
 * The IntelliJ Platform allows for persisting component states (see {@link State} and {@link Storage}).
 * It is possible to specify which file should hold persisting data.
 * There are a number of standard file system anchors like 'workspace file',
 * 'project config dir', which can be used for defining a storage file's path.
 * <p>
 * The platform provides special support for such anchors in the form of macros,
 * i.e., special markers that are mapped to the current file system environment at runtime.
 * <p>
 * This class holds those markers and utility method for working with them.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html">Persisting State of Components (IntelliJ Platform Docs)</a>
 */
public final class StoragePathMacros {
  /**
   * {@link com.intellij.openapi.project.Project#getWorkspaceFile() Workspace} file key.
   * {@code 'Workspace file'} holds settings that are local to a particular environment
   * and should not be shared with other team members.
   */
  public static final String WORKSPACE_FILE = "$WORKSPACE_FILE$";

  /**
   * Storage file for cache-like data. Stored outside of project directory (if project level component)
   * and outside of application configuration directory (if application level component).
   */
  public static final String CACHE_FILE = "$CACHE_FILE$";

  /**
   * Same as {@link #WORKSPACE_FILE}, but stored per-product. Applicable only for project-level.
   */
  public static final String PRODUCT_WORKSPACE_FILE = "$PRODUCT_WORKSPACE_FILE$";

  public static final String MODULE_FILE = "$MODULE_FILE$";

  @Internal
  public static final String PROJECT_DEFAULT_FILE = "project.default.xml";

  /**
   * Application level non-roamable storage.
   */
  public static final String NON_ROAMABLE_FILE = "other.xml";

  @Internal
  public static final String APP_INTERNAL_STATE_DB = "app-internal-state.db";

  @Internal
  public static final String PROJECT_FILE = "$PROJECT_FILE$";

  private StoragePathMacros() { }
}
