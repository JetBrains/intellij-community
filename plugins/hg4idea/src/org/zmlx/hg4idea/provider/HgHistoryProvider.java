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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.*;
import com.intellij.util.ui.*;
import com.intellij.vcsUtil.*;
import org.jetbrains.annotations.*;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.*;

import javax.swing.*;
import java.util.*;

public class HgHistoryProvider implements VcsHistoryProvider {

  private static final int DEFAULT_LIMIT = 500;

  private final Project project;

  public HgHistoryProvider(Project project) {
    this.project = project;
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
    final VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, filePath);
    if (vcsRoot == null) {
      return null;
    }
    HgFile hgFile = new HgFile(vcsRoot, filePath);
    List<HgFileRevision> revisions = getHistory(filePath, vcsRoot, project, DEFAULT_LIMIT);
    final List<VcsFileRevision> result = new LinkedList<VcsFileRevision>(revisions);
    return new VcsHistorySession() {
      @Override
      public VcsRevisionNumber getCurrentRevisionNumber() {
        return new HgWorkingCopyRevisionsCommand(project).firstParent(vcsRoot);
      }

      @Override
      public HistoryAsTreeProvider getHistoryAsTreeProvider() {
        return null;
      }

      @Override
      public List<VcsFileRevision> getRevisionList() {
        return result;
      }

      @Override
      public boolean isCurrentRevision(VcsRevisionNumber vcsRevisionNumber) {
        return vcsRevisionNumber.equals(getCurrentRevisionNumber());
      }

      @Override
      public boolean shouldBeRefreshed() {
        return false;
      }

      @Override
      public boolean allowAsyncRefresh() {
        return false;
      }

      @Override
      public boolean isContentAvailable(VcsFileRevision vcsFileRevision) {
        return false;
      }
    };
  }

  public void reportAppendableHistory(FilePath filePath, final VcsAppendableHistorySessionPartner partner) throws VcsException {
    final VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, filePath);
    List<HgFileRevision> history = getHistory(filePath, vcsRoot, this.project, DEFAULT_LIMIT);

    if (history.size() == 0) return;
    
    final VcsAbstractHistorySession emptySession = new VcsAbstractHistorySession(Collections.<VcsFileRevision>emptyList()) {
      @Nullable
      protected VcsRevisionNumber calcCurrentRevisionNumber() {
        return new HgWorkingCopyRevisionsCommand(project).firstParent(vcsRoot);
      }

      public HistoryAsTreeProvider getHistoryAsTreeProvider() {
        return null;
      }
    };
    partner.reportCreatedEmptySession(emptySession);

    for (HgFileRevision hgFileRevision : history) {
      partner.acceptRevision(hgFileRevision);
    }
    partner.finished();
  }

  private List<HgFileRevision> getHistory(FilePath filePath, VirtualFile vcsRoot, Project project, int limit) {
    HgLogCommand logCommand = new HgLogCommand(project);
    logCommand.setFollowCopies(true);
    logCommand.setIncludeRemoved(true);

    return logCommand.execute(new HgFile(vcsRoot, filePath), limit, false);
  }

  public boolean supportsHistoryForDirectories() {
    return false;
  }
}
