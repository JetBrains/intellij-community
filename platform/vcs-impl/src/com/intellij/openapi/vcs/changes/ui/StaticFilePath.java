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
import org.jetbrains.annotations.Nullable;

public class StaticFilePath {
  private final String myKey;
  private final String myPath;
  private final boolean myIsDirectory;
  // todo?
  private final VirtualFile myVf;

  public StaticFilePath(boolean isDirectory, String path, VirtualFile vf) {
    myIsDirectory = isDirectory;
    myPath = path;
    myVf = vf;
    myKey = FilePathsHelper.convertPath(path);
  }

  private StaticFilePath(boolean isDirectory, String path, final String key, final VirtualFile vf) {
    myIsDirectory = isDirectory;
    myPath = path;
    myVf = vf;
    myKey = key;
  }

  public boolean isDirectory() {
    return myIsDirectory;
  }

  public String getPath() {
    return myPath;
  }

  public String getKey() {
    return myKey;
  }

  public VirtualFile getVf() {
    return myVf;
  }

  @Nullable
  public StaticFilePath getParent() {
    final int idx = myKey.lastIndexOf('/');
    return (idx == -1) || (idx == 0) ? null :
           new StaticFilePath(true, myPath.substring(0, idx), myKey.substring(0, idx), myVf == null ? null : myVf.getParent());
  }
}
