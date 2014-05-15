// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import com.google.common.base.Objects;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;

public class HgFile {

  private final VirtualFile vcsRoot;
  private final File file;

  private String relativePath;

  public HgFile(@NotNull VirtualFile vcsRoot, File file) {
    this.vcsRoot = vcsRoot;
    this.file = file;
  }

  public HgFile(@NotNull VirtualFile vcsRoot, FilePath filePath) {
    this(vcsRoot, filePath.getIOFile());
  }

  public HgFile(@NotNull Project project, @NotNull VirtualFile file) {
    this(HgUtil.getHgRootOrNull(project, file), VcsUtil.getFilePath(file.getPath()));
  }

  @NotNull
  public VirtualFile getRepo() {
    return vcsRoot;
  }

  public File getFile() {
    return file;
  }

  @Nullable
  public String getRelativePath() {
    if (relativePath == null) {
      //For configuration like "d:/.hg" File.getParent method has minimal prefix length, so vcsRoot will be "d:", getParent will be "d:/".
      relativePath = FileUtil.getRelativePath(VfsUtilCore.virtualToIoFile(vcsRoot), file);
    }
    return relativePath;
  }

  @NotNull
  public FilePath toFilePath() {
    return VcsUtil.getFilePath(file);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    HgFile that = (HgFile)o;

    if (!vcsRoot.equals(that.vcsRoot)) {
      return false;
    }
    if (file != null ? !FileUtil.filesEqual(file, that.file) : that.file != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(vcsRoot, file);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(HgFile.class)
      .add("repo", vcsRoot)
      .add("file", file)
      .add("relativePath", getRelativePath())
      .toString();
  }
}
