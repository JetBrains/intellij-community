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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsUtil;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.command.HgLogCommand;

import javax.swing.*;
import java.util.List;

public class HgHistoryProvider implements VcsHistoryProvider {

  private static final int DEFAULT_LIMIT = 500;

  private final Project myProject;

  public HgHistoryProvider(Project project) {
    this.myProject = project;
  }

  public VcsDependentHistoryComponents getUICustomization(VcsHistorySession session, JComponent forShortcutRegistration) {
    return VcsDependentHistoryComponents.createOnlyColumns(new ColumnInfo[0]);
  }

  public boolean isDateOmittable() {
    return false;
  }

  public String getHelpId() {
    return null;
  }

  public VcsHistorySession createSessionFor(FilePath filePath) throws VcsException {
    final VirtualFile vcsRoot = VcsUtil.getVcsRootFor(myProject, filePath);
    if (vcsRoot == null) {
      return null;
    }
    final HgLogCommand logCommand = new HgLogCommand(myProject);
    logCommand.setFollowCopies(true);
    final HgFile file = new HgFile(vcsRoot, filePath);
    final List<HgFileRevision> revisions = logCommand.execute(file, DEFAULT_LIMIT);

    return new VcsAbstractHistorySession(revisions) {
      protected VcsRevisionNumber calcCurrentRevisionNumber() {
        return logCommand.execute(file, 1).get(0).getRevisionNumber();
      }

      public HistoryAsTreeProvider getHistoryAsTreeProvider() {
        return null;
      }
    };
  }

  public AnAction[] getAdditionalActions(Runnable runnable) {
    return new AnAction[0];
  }

  public void reportAppendableHistory(FilePath filePath, final VcsAppendableHistorySessionPartner partner) throws VcsException {
    final VcsHistorySession emptySession = createSessionFor(filePath);
    partner.reportCreatedEmptySession((VcsAbstractHistorySession) emptySession);
  }

  public boolean supportsHistoryForDirectories() {
    return false;
  }

}
