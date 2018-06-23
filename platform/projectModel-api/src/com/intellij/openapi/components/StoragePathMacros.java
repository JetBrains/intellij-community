// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * We have a framework for persisting component states (see {@link State} {@link Storage}). It allows to specify which file
 * should hold persisting data. There is a number of standard file system anchors like 'workspace file', 'project config dir' which
 * can be used for defining a storage file's path. Hence, IJ provides special support for such anchors in the form of macros,
 * i.e. special markers that are mapped to the current file system environment at runtime.
 * <p/>
 * This class holds those markers and utility method for working with them.
 *
 * @author Denis Zhdanov
 * @since 5/2/12 12:57 PM
 */
public class StoragePathMacros {
  /**
   * {@link Project#getWorkspaceFile() Workspace} file key.
   * {@code 'Workspace file'} holds settings that are local to a particular environment and should not be shared with another
   * team members.
   */
  @NonNls
  @NotNull
  public static final String WORKSPACE_FILE = "$WORKSPACE_FILE$";

  @NonNls @NotNull public static final String MODULE_FILE = "$MODULE_FILE$";

  private StoragePathMacros() {
  }
}
