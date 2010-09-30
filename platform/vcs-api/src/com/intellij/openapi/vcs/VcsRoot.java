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

/**
 * @author yole
 */
public class VcsRoot {
  public final AbstractVcs vcs;
  public final VirtualFile path;
  private int hashcode;

  public VcsRoot(final AbstractVcs vcs, final VirtualFile path) {
    this.vcs = vcs;
    this.path = path;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsRoot root = (VcsRoot)o;

    if (path != null ? !path.equals(root.path) : root.path != null) return false;
    if (vcs != null ? !vcs.getName().equals(root.vcs.getName()) : root.vcs != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    if (hashcode == 0) {
      hashcode = vcs != null ? vcs.getName().hashCode() : 0;
      hashcode = 31 * hashcode + (path != null ? path.hashCode() : 0);
    }
    return hashcode;
  }
}