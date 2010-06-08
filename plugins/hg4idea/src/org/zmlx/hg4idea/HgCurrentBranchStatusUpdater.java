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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.zmlx.hg4idea.command.HgTagBranchCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.ui.HgCurrentBranchStatus;

import java.util.Collections;
import java.util.List;

class HgCurrentBranchStatusUpdater implements HgUpdater {

  private final HgCurrentBranchStatus hgCurrentBranchStatus;

  public HgCurrentBranchStatusUpdater(HgCurrentBranchStatus hgCurrentBranchStatus) {
    this.hgCurrentBranchStatus = hgCurrentBranchStatus;
  }

  public void update(Project project) {
    Editor textEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (textEditor != null) {
      Document document = textEditor.getDocument();
      VirtualFile file = FileDocumentManager.getInstance().getFile(document);

      VirtualFile repo = VcsUtil.getVcsRootFor(project, file);
      if (repo != null) {
        HgTagBranchCommand hgTagBranchCommand = new HgTagBranchCommand(project, repo);
        String branch = hgTagBranchCommand.getCurrentBranch();
        List<HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(project).parents(repo);
        hgCurrentBranchStatus.updateFor(branch, parents);
        return;
      }
    }
    hgCurrentBranchStatus.updateFor(null, Collections.<HgRevisionNumber>emptyList());
  }

}
