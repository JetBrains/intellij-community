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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HgHistoryProvider implements VcsHistoryProvider {

  private final Project myProject;

  public HgHistoryProvider(Project project) {
    myProject = project;
  }

  public VcsDependentHistoryComponents getUICustomization(VcsHistorySession session,
                                                          JComponent forShortcutRegistration) {
    return VcsDependentHistoryComponents.createOnlyColumns(new ColumnInfo[0]);
  }

  public AnAction[] getAdditionalActions(Runnable runnable) {
    return new AnAction[]{ShowAllAffectedGenericAction.getInstance(),
      ActionManager.getInstance().getAction(VcsActions.ACTION_COPY_REVISION_NUMBER)};
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
    revisions.addAll(getHistory(filePath, vcsRoot, myProject));
    return createAppendableSession(vcsRoot, revisions, null);
  }

  public void reportAppendableHistory(FilePath filePath, final VcsAppendableHistorySessionPartner partner) throws VcsException {
    final VirtualFile vcsRoot = HgUtil.getHgRootOrThrow(myProject, filePath);

    final List<HgFileRevision> history = getHistory(filePath, vcsRoot, myProject);
    if (history.size() == 0) return;

    final VcsAbstractHistorySession emptySession = createAppendableSession(vcsRoot, Collections.<VcsFileRevision>emptyList(), null);
    partner.reportCreatedEmptySession(emptySession);

    for (HgFileRevision hgFileRevision : history) {
      partner.acceptRevision(hgFileRevision);
    }
    partner.finished();
  }

  private VcsAbstractHistorySession createAppendableSession(final VirtualFile vcsRoot, List<VcsFileRevision> revisions, @Nullable VcsRevisionNumber number) {
    return new VcsAbstractHistorySession(revisions, number) {
      @Nullable
      protected VcsRevisionNumber calcCurrentRevisionNumber() {
        return new HgWorkingCopyRevisionsCommand(myProject).firstParent(vcsRoot);
      }

      public HistoryAsTreeProvider getHistoryAsTreeProvider() {
        return null;
      }

      @Override
      public VcsHistorySession copy() {
        return createAppendableSession(vcsRoot, getRevisionList(), getCurrentRevisionNumber());
      }
    };
  }

  public static List<HgFileRevision> getHistory(@NotNull FilePath filePath,
                                                @NotNull VirtualFile vcsRoot,
                                                @NotNull Project project) {
    VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(project);
    return getHistory(filePath, vcsRoot, project, null, vcsConfiguration.LIMIT_HISTORY ? vcsConfiguration.MAXIMUM_HISTORY_ROWS : -1);
  }

  public static List<HgFileRevision> getHistory(@NotNull FilePath filePath,
                                                @NotNull VirtualFile vcsRoot,
                                                @NotNull Project project,
                                                @Nullable HgRevisionNumber revisionNumber, int limit) {
    final HgLogCommand logCommand = new HgLogCommand(project);
    logCommand.setFollowCopies(!filePath.isDirectory());
    logCommand.setIncludeRemoved(true);
    List<String> args = new ArrayList<String>();
    String revNumberAsArg = revisionNumber == null ? "." : revisionNumber.getChangeset();
    args.add("--rev");
    args.add("reverse(0::" + revNumberAsArg + ")"); // without --follow was 0:tip by default without --rev;
    // reverse needed because of mercurial default order problem -r revset with and without -f option
    try {
      return logCommand.execute(new HgFile(vcsRoot, filePath), limit, false, args);
    }
    catch (HgCommandException e) {
      new HgCommandResultNotifier(project).notifyError(null, HgVcsMessages.message("hg4idea.error.log.command.execution"), e.getMessage());
      return Collections.emptyList();
    }
  }

  public boolean supportsHistoryForDirectories() {
    return true;
  }

  @Override
  public DiffFromHistoryHandler getHistoryDiffHandler() {
    return new HgDiffFromHistoryHandler(myProject);
  }

  @Override
  public boolean canShowHistoryFor(@NotNull VirtualFile file) {
    return true;
  }
}
