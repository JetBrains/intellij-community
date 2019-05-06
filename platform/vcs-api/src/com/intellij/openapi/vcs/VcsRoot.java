/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class VcsRoot {

  @Nullable private final AbstractVcs myVcs;
  @Nullable private final VirtualFile myPath;

  private int hashcode;

  public VcsRoot(@Nullable AbstractVcs vcs, @Nullable  VirtualFile path) {
    myVcs = vcs;
    myPath = path;
  }

  @Nullable
  public AbstractVcs getVcs() {
    return myVcs;
  }

  @Nullable
  public VirtualFile getPath() {
    return myPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsRoot root = (VcsRoot)o;

    if (myPath != null ? !myPath.equals(root.myPath) : root.myPath != null) return false;
    if (myVcs != null ? !myVcs.equals(root.myVcs) : root.myVcs != null) return false;

    return true;
  }

  public int hashCode() {
    if (hashcode == 0) {
      hashcode = myVcs != null ? myVcs.hashCode() : 0;
      hashcode = 31 * hashcode + (myPath != null ? myPath.hashCode() : 0);
    }
    return hashcode;
  }

  @Override
  public String toString() {
    return String.format("VcsRoot{vcs=%s, path=%s}", myVcs, myPath);
  }
}