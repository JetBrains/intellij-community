/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StaticFilePath {
  private final String myKey;
  private final String myPath;
  private final boolean myIsDirectory;
  private final VirtualFile myVf;

  public StaticFilePath(boolean isDirectory, @NotNull String path, @Nullable VirtualFile vf) {
    this(isDirectory, path, FilePathsHelper.convertPath(path), vf);
  }

  private StaticFilePath(boolean isDirectory, @NotNull String path, @NotNull String key, @Nullable VirtualFile vf) {
    myIsDirectory = isDirectory;
    myPath = path;
    myKey = key;
    myVf = vf;
  }

  public boolean isDirectory() {
    return myIsDirectory;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @NotNull
  public String getKey() {
    return myKey;
  }

  @Nullable
  public VirtualFile getVf() {
    return myVf;
  }

  @Nullable
  public StaticFilePath getParent() {
    final int idx = myKey.lastIndexOf('/');
    if (idx == -1 || idx == 0) return null;
    return new StaticFilePath(true, myPath.substring(0, idx), myKey.substring(0, idx), myVf == null ? null : myVf.getParent());
  }
}
