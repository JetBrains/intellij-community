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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsRoot;

public class FilePathUnderVcs {
  private final FilePath myPath;
  private final AbstractVcs myVcs;
  private int hashcode;

  public FilePathUnderVcs(final FilePath path, final AbstractVcs vcs) {
    myPath = path;
    myVcs = vcs;
  }

  FilePathUnderVcs(final VcsRoot root) {
    myPath = new FilePathImpl(root.path);
    myVcs = root.vcs;
  }

  public FilePath getPath() {
    return myPath;
  }

  public AbstractVcs getVcs() {
    return myVcs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FilePathUnderVcs that = (FilePathUnderVcs)o;

    if (myPath != null ? !myPath.equals(that.myPath) : that.myPath != null) return false;
    if (myVcs != null ? ! Comparing.equal(myVcs.getName(), that.myVcs.getName()) : that.myVcs != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    if (hashcode == 0) {
      hashcode = myPath != null ? myPath.hashCode() : 0;
      hashcode = 31 * hashcode + (myVcs != null ? myVcs.getName().hashCode() : 0);
    }
    return hashcode;
  }
}
