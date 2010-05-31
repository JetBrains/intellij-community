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

import com.intellij.openapi.project.*;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.*;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.*;

public class HgAnnotationProvider implements AnnotationProvider {

  private static final int DEFAULT_LIMIT = 500;

  private final Project project;

  public HgAnnotationProvider(Project project) {
    this.project = project;
  }

  public FileAnnotation annotate(VirtualFile file) throws VcsException {
    VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, file);
    if (vcsRoot == null) {
      throw new VcsException("vcs root is null");
    }
    HgFile hgFile = new HgFile(vcsRoot, VfsUtil.virtualToIoFile(file));
    HgAnnotateCommand hgAnnotateCommand = new HgAnnotateCommand(project);
    HgLogCommand hgLogCommand = new HgLogCommand(project);
    return new HgAnnotation(
      hgFile,
      hgAnnotateCommand.execute(hgFile),
      hgLogCommand.execute(hgFile, DEFAULT_LIMIT, false)
    );
  }

  public FileAnnotation annotate(VirtualFile file, VcsFileRevision revision) throws VcsException {
    return annotate(file);
  }

  public boolean isAnnotationValid(VcsFileRevision rev) {
    return true;
  }

}
