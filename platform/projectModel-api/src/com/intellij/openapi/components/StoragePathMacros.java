// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import org.jetbrains.annotations.NotNull;

/**
 * We have a framework for persisting component states (see {@link State} {@link Storage}).
 * It allows specifying which file should hold persisting data.
 * There are a number of standard file system anchors like 'workspace file', 'project config dir' which
 * can be used for defining a storage file's path.
 * Hence, IJ provides special support for such anchors in the form of macros,
 * i.e., special markers that are mapped to the current file system environment at runtime.
 * <p/>
 * This class holds those markers and utility method for working with them.
 */
public final class StoragePathMacros {
  /**
   * {@link com.intellij.openapi.project.Project#getWorkspaceFile() Workspace} file key.
   * {@code 'Workspace file'} holds settings that are local to a particular environment
   * and should not be shared with other team members.
   */
  public static final @NotNull String WORKSPACE_FILE = "$WORKSPACE_FILE$";

  /**
   * Storage file for cache-like data. Stored outside of project directory (if project level component)
   * and outside of application configuration directory (if application level component).
   */
  public static final String CACHE_FILE = "$CACHE_FILE$";

  /**
   * Same as {@link #WORKSPACE_FILE}, but stored per-product. Applicable only for project-level.
   */
  public static final @NotNull String PRODUCT_WORKSPACE_FILE = "$PRODUCT_WORKSPACE_FILE$";

  public static final @NotNull String MODULE_FILE = "$MODULE_FILE$";

  /**
   * Application level non-roamable storage.
   */
  public static final String NON_ROAMABLE_FILE = "other.xml";

  private StoragePathMacros() {
  }
}
