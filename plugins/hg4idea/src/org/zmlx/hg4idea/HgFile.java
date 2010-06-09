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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.jetbrains.annotations.NotNull;

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
    this(VcsUtil.getVcsRootFor(project, file), VcsUtil.getFilePath(file.getPath()));
  }

  @NotNull
  public VirtualFile getRepo() {
    return vcsRoot;
  }

  public File getFile() {
    return file;
  }

  @NotNull
  public String getRelativePath() {
    if (relativePath == null) {
      relativePath = buildRelativePath(VfsUtil.virtualToIoFile(vcsRoot), file);
    }
    return relativePath;
  }

  @NotNull
  public FilePath toFilePath() {
    return ApplicationManager.getApplication().runReadAction(
      new Computable<FilePath>() {
      public FilePath compute() {
        return VcsUtil.getFilePath(file);
      }
    });
  }

  private static String buildRelativePath(File anchestor, File descendant) {
    if (anchestor.equals(descendant.getParentFile())) {
      return descendant.getName();
    }
    return buildRelativePath(anchestor, descendant.getParentFile())
      + File.separator + descendant.getName();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    }
    if (!(object instanceof HgFile)) {
      return false;
    }
    HgFile that = (HgFile) object;
    return new EqualsBuilder()
      .append(vcsRoot, that.vcsRoot)
      .append(file, that.file)
      .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
      .append(vcsRoot)
      .append(file)
      .toHashCode();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
      .append("repo", vcsRoot)
      .append("file", file)
      .append("relativePath", getRelativePath())
      .toString();
  }
}
