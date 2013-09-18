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
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgAnnotateCommand;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.List;

public class HgAnnotationProvider implements AnnotationProvider {

  private static final int DEFAULT_LIMIT = 500;

  @NotNull private final Project myProject;

  public HgAnnotationProvider(@NotNull Project project) {
    myProject = project;
  }

  public FileAnnotation annotate(VirtualFile file) throws VcsException {
    return annotate(file, null);
  }

  public FileAnnotation annotate(VirtualFile file, VcsFileRevision revision) throws VcsException {
    final VirtualFile vcsRoot = VcsUtil.getVcsRootFor(myProject, VcsUtil.getFilePath(file.getPath()));
    if (vcsRoot == null) {
      throw new VcsException("vcs root is null for " + file);
    }
    final HgFile hgFile = new HgFile(vcsRoot, VfsUtilCore.virtualToIoFile(file));
    HgFile fileToAnnotate = revision instanceof HgFileRevision ? HgUtil
      .getFileNameInTargetRevision(myProject, ((HgFileRevision)revision).getRevisionNumber(), hgFile) : hgFile;
    final List<HgAnnotationLine> annotationResult = (new HgAnnotateCommand(myProject)).execute(fileToAnnotate, revision);
    final List<HgFileRevision> logResult;
    try {
      logResult = (new HgLogCommand(myProject)).execute(fileToAnnotate, DEFAULT_LIMIT, false);
    }
    catch (HgCommandException e) {
      throw new VcsException("Can not annotate, " + HgVcsMessages.message("hg4idea.error.log.command.execution"), e);
    }
    VcsRevisionNumber revisionNumber = revision == null ?
                                       new HgWorkingCopyRevisionsCommand(myProject).tip(vcsRoot) :
                                       revision.getRevisionNumber();
    return new HgAnnotation(myProject, hgFile, annotationResult, logResult, revisionNumber);
  }

  public boolean isAnnotationValid(VcsFileRevision rev) {
    return true;
  }
}
