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
package org.zmlx.hg4idea.provider.annotate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.command.HgAnnotateCommand;
import org.zmlx.hg4idea.command.HgLogCommand;

public class HgAnnotationProvider implements AnnotationProvider {

  private static final int DEFAULT_LIMIT = 500;

  private final Project myProject;

  public HgAnnotationProvider(Project project) {
    this.myProject = project;
  }

  public FileAnnotation annotate(VirtualFile file) throws VcsException {
    final VirtualFile vcsRoot = VcsUtil.getVcsRootFor(myProject, VcsUtil.getFilePath(file.getPath()));
    if (vcsRoot == null) {
      throw new VcsException("vcs root is null");
    }
    final HgFile hgFile = new HgFile(vcsRoot, VfsUtil.virtualToIoFile(file));
    return new HgAnnotation(
      hgFile,
      (new HgAnnotateCommand(myProject)).execute(hgFile),
      (new HgLogCommand(myProject)).execute(hgFile, DEFAULT_LIMIT, false)
    );
  }

  public FileAnnotation annotate(VirtualFile file, VcsFileRevision revision) throws VcsException {
    return annotate(file);
  }

  public boolean isAnnotationValid(VcsFileRevision rev) {
    return true;
  }

}
