/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  @Deprecated
  @NotNull
  public static final String ROOT_CONFIG = "$ROOT_CONFIG$";

  /**
   * Points to the application-level options root directory.
   * @deprecated Not required anymore. See {@link State#storages()}. In short: specify relative path instead (without macro).
   */
  @Deprecated
  public static final String APP_CONFIG = "$APP_CONFIG$";

  /**
   * @deprecated Not required anymore. See {@link State#storages()}.
   */
  @Deprecated
  public static final String PROJECT_FILE = "$PROJECT_FILE$";

  /**
   * @deprecated Not required anymore. See {@link State#storages()}.
   */
  @Deprecated
  public static final String PROJECT_CONFIG_DIR = "$PROJECT_CONFIG_DIR$";

  /**
   * {@link Project#getWorkspaceFile() Workspace} file key.
   * <p/>
   * {@code 'Workspace file'} holds settings that are local to a particular environment and should not be shared with another
   * team members.
   */
  @NonNls @NotNull public static final String WORKSPACE_FILE = "$WORKSPACE_FILE$";

  @NonNls @NotNull public static final String MODULE_FILE = "$MODULE_FILE$";

  private StoragePathMacros() {
  }
}
