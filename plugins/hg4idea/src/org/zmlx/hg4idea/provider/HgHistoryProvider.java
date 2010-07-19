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
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgUtil;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HgHistoryProvider implements VcsHistoryProvider {

  private static final int DEFAULT_LIMIT = 500;

  private final Project myProject;

  public HgHistoryProvider(Project project) {
    myProject = project;
  }

  public VcsDependentHistoryComponents getUICustomization(VcsHistorySession session,
    JComponent forShortcutRegistration) {
    return VcsDependentHistoryComponents.createOnlyColumns(new ColumnInfo[0]);
  }

  public AnAction[] getAdditionalActions(Runnable runnable) {
    return new AnAction[0];
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
    final List<VcsFileRevision> revisions = new ArrayList<VcsFileRevision>();
    revisions.addAll(getHistory(filePath, vcsRoot, myProject, DEFAULT_LIMIT));
    return createAppendableSession(vcsRoot, revisions);
  }

  public void reportAppendableHistory(FilePath filePath, final VcsAppendableHistorySessionPartner partner) throws VcsException {
    final VirtualFile vcsRoot = HgUtil.getHgRootOrThrow(myProject, filePath);

    final List<HgFileRevision> history = getHistory(filePath, vcsRoot, myProject, DEFAULT_LIMIT);
    if (history.size() == 0) return;

    final VcsAbstractHistorySession emptySession = createAppendableSession(vcsRoot, Collections.<VcsFileRevision>emptyList());
    partner.reportCreatedEmptySession(emptySession);

    for (HgFileRevision hgFileRevision : history) {
      partner.acceptRevision(hgFileRevision);
    }
    partner.finished();
  }

  private VcsAbstractHistorySession createAppendableSession(final VirtualFile vcsRoot, List<VcsFileRevision> revisions) {
    return new VcsAbstractHistorySession(revisions) {
      @Nullable
      protected VcsRevisionNumber calcCurrentRevisionNumber() {
        return new HgWorkingCopyRevisionsCommand(myProject).firstParent(vcsRoot);
      }

      public HistoryAsTreeProvider getHistoryAsTreeProvider() {
        return null;
      }

      @Override
      public VcsHistorySession copy() {
        return createAppendableSession(vcsRoot, getRevisionList());
      }
    };
  }

  private static List<HgFileRevision> getHistory(FilePath filePath, VirtualFile vcsRoot, Project project, int limit) {
    final HgLogCommand logCommand = new HgLogCommand(project);
    logCommand.setFollowCopies(true);
    logCommand.setIncludeRemoved(true);
    return logCommand.execute(new HgFile(vcsRoot, filePath), limit, false);
  }

  public boolean supportsHistoryForDirectories() {
    return false;
  }
}
