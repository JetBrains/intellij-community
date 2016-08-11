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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.log.HgBaseLogParser;
import org.zmlx.hg4idea.log.HgFileRevisionLogParser;
import org.zmlx.hg4idea.log.HgHistoryUtil;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

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
    return VcsDependentHistoryComponents.createOnlyColumns(ColumnInfo.EMPTY_ARRAY);
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
    final List<VcsFileRevision> revisions = new ArrayList<>();
    revisions.addAll(getHistory(filePath, vcsRoot, myProject));
    return createAppendableSession(vcsRoot, revisions, null);
  }

  public void reportAppendableHistory(FilePath filePath, final VcsAppendableHistorySessionPartner partner) throws VcsException {
    final VirtualFile vcsRoot = HgUtil.getHgRootOrThrow(myProject, filePath);

    final List<HgFileRevision> history = getHistory(filePath, vcsRoot, myProject);
    if (history.size() == 0) return;

    final VcsAbstractHistorySession emptySession = createAppendableSession(vcsRoot, Collections.emptyList(), null);
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
 /*  The standard way to get history following renames is to call hg log --follow. However:
  1. It is broken in case of uncommitted rename (i.e. if the file is currently renamed in the working dir):
    in this case we use a special python template "follow(path)" which handles this case.
  2. We don't use this python "follow(path)" function for all cases, because it is fully supported only since hg 2.6,
   and it is a bit slower and possibly less reliable than plain --follow parameter.
  3. It doesn't work with "-r": in this case --follow is simply ignored (hg commit 24208:8b4b9ee6001a).
   As a workaround we could use the same follow(path) python, but this function requires current name of the file,
    which is unknown in case of "-r", and identifying it would be very slow.

    As a result we don't follow renames in annotate called from diff or from an old revision, which we can survive.
*/
    FilePath originalFilePath = HgUtil.getOriginalFileName(filePath, ChangeListManager.getInstance(project));
    if (revisionNumber == null && !filePath.isDirectory() && !filePath.equals(originalFilePath)) {
      // uncommitted renames detected
      return getHistoryForUncommittedRenamed(originalFilePath, vcsRoot, project, limit);
    }
    final HgLogCommand logCommand = new HgLogCommand(project);
    logCommand.setFollowCopies(!filePath.isDirectory());
    logCommand.setIncludeRemoved(true);
    List<String> args = new ArrayList<>();
    if (revisionNumber != null) {
      args.add("--rev");
      args.add("reverse(0::" + revisionNumber.getChangeset() + ")");
    }
    return logCommand.execute(new HgFile(vcsRoot, filePath), limit, false, args);
  }

  /**
   * Workaround for getting follow file history in case of uncommitted move/rename change
   */
  private static List<HgFileRevision> getHistoryForUncommittedRenamed(@NotNull FilePath originalHgFilePath,
                                                                      @NotNull VirtualFile vcsRoot,
                                                                      @NotNull Project project, int limit) {
    HgFile originalHgFile = new HgFile(vcsRoot, originalHgFilePath);
    final HgLogCommand logCommand = new HgLogCommand(project);
    logCommand.setIncludeRemoved(true);
    final HgVersion version = logCommand.getVersion();
    String[] templates = HgBaseLogParser.constructFullTemplateArgument(false, version);
    String template = HgChangesetUtil.makeTemplate(templates);
    List<String> argsForCmd = ContainerUtil.newArrayList();
    String relativePath = originalHgFile.getRelativePath();
    argsForCmd.add("--rev");
    argsForCmd
      .add(String.format("reverse(follow(%s))", relativePath != null ? "'" + FileUtil.toSystemIndependentName(relativePath) + "'" : ""));
    HgCommandResult result = logCommand.execute(vcsRoot, template, limit, relativePath != null ? null : originalHgFile, argsForCmd);
    return HgHistoryUtil.getCommitRecords(project, result, new HgFileRevisionLogParser(project, originalHgFile, version));
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
