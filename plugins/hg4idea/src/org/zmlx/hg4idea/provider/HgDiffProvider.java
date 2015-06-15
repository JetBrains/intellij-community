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
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.zmlx.hg4idea.HgContentRevision;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;

public class HgDiffProvider implements DiffProvider {

  private final Project project;

  public HgDiffProvider(Project project) {
    this.project = project;
  }

  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, file);
    if (vcsRoot == null) {
      return null;
    }

    FilePath filePath = ObjectsConvertor.VIRTUAL_FILEPATH.convert(file);
    return new HgWorkingCopyRevisionsCommand(project).parents(vcsRoot, filePath).first;
  }

  public ItemLatestState getLastRevision(VirtualFile file) {
    VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, file);
    if (vcsRoot == null) {
      return null;
    }
    HgRevisionNumber revision = (HgRevisionNumber) getCurrentRevision(file);
    if (revision == null) {
      return null;
    }
    return new ItemLatestState(revision, file.exists(), true);
  }

  public ItemLatestState getLastRevision(FilePath filePath) {
    VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, filePath);
    if (vcsRoot == null) {
      return null;
    }

    HgWorkingCopyRevisionsCommand command = new HgWorkingCopyRevisionsCommand(project);
    HgRevisionNumber currentRevision = command.identify(vcsRoot).getFirst();
    if (currentRevision == null) {
      return null;
    }

    boolean fileExists = filePath.getIOFile().exists();
    if (currentRevision.isWorkingVersion()) {
      return new ItemLatestState(command.firstParent(vcsRoot), fileExists, true);
    }

    return new ItemLatestState(currentRevision, fileExists, true);
  }

  public VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot) {
    return new HgWorkingCopyRevisionsCommand(project).tip(vcsRoot);
  }

  public ContentRevision createFileContent(VcsRevisionNumber revisionNumber, VirtualFile file) {
    if (file == null) {
      return null;
    }

    VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, file);
    if (vcsRoot == null) {
      return null;
    }

    HgRevisionNumber hgRevisionNumber = (HgRevisionNumber) revisionNumber;
    if (hgRevisionNumber.isWorkingVersion()) {
      throw new IllegalStateException("Should not compare against working copy");
    }
    HgFile hgFile = new HgFile(vcsRoot, VfsUtilCore.virtualToIoFile(file));
    return HgContentRevision.create(project, hgFile, hgRevisionNumber);
  }

}
