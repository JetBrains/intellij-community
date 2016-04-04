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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.HgAnnotateCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.provider.HgHistoryProvider;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.List;

public class HgAnnotationProvider implements AnnotationProviderEx {

  @NotNull private final Project myProject;

  public HgAnnotationProvider(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public FileAnnotation annotate(@NotNull VirtualFile file) throws VcsException {
    return annotate(file, null);
  }

  @NotNull
  public FileAnnotation annotate(@NotNull VirtualFile file, VcsFileRevision revision) throws VcsException {
    final VirtualFile vcsRoot = VcsUtil.getVcsRootFor(myProject, VcsUtil.getFilePath(file.getPath()));
    if (vcsRoot == null) {
      throw new VcsException("vcs root is null for " + file);
    }
    HgRevisionNumber revisionNumber = revision != null ? (HgRevisionNumber)revision.getRevisionNumber() : null;
    final HgFile hgFile = new HgFile(vcsRoot, VfsUtilCore.virtualToIoFile(file));
    HgFile fileToAnnotate = revision instanceof HgFileRevision
                            ? HgUtil.getFileNameInTargetRevision(myProject, revisionNumber, hgFile)
                            : new HgFile(vcsRoot,
                                         HgUtil.getOriginalFileName(hgFile.toFilePath(), ChangeListManager.getInstance(myProject)));
    final List<HgAnnotationLine> annotationResult = (new HgAnnotateCommand(myProject)).execute(fileToAnnotate, revisionNumber);
    final List<HgFileRevision> logResult = HgHistoryProvider.getHistory(fileToAnnotate.toFilePath(), vcsRoot, myProject, null, -1);
    return new HgAnnotation(myProject, hgFile, annotationResult, logResult,
                            revisionNumber != null ? revisionNumber : new HgWorkingCopyRevisionsCommand(myProject).tip(vcsRoot));
  }

  @NotNull
  @Override
  public FileAnnotation annotate(@NotNull FilePath path, @NotNull VcsRevisionNumber revision) throws VcsException {
    final VirtualFile vcsRoot = VcsUtil.getVcsRootFor(myProject, path);
    if (vcsRoot == null) {
      throw new VcsException("vcs root is null for " + path);
    }
    final HgFile hgFile = new HgFile(vcsRoot, path);
    final List<HgAnnotationLine> annotationResult = (new HgAnnotateCommand(myProject)).execute(hgFile, (HgRevisionNumber)revision);
    final List<HgFileRevision> logResult = HgHistoryProvider
      .getHistory(hgFile.toFilePath(), vcsRoot, myProject, (HgRevisionNumber)revision, -1);
    return new HgAnnotation(myProject, hgFile, annotationResult, logResult, revision);
  }

  public boolean isAnnotationValid(@NotNull VcsFileRevision rev) {
    return true;
  }
}
