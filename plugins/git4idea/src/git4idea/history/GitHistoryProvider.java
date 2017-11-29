/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.config.GitExecutableValidator;
import git4idea.history.browser.SHAHash;
import git4idea.log.GitShowCommitInLogAction;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Git history provider implementation
 */
public class GitHistoryProvider implements VcsHistoryProviderEx,
                                           VcsCacheableHistorySessionFactory<Boolean, VcsAbstractHistorySession>,
                                           VcsBaseRevisionAdviser {
  private static final Logger LOG = Logger.getInstance(GitHistoryProvider.class.getName());

  @NotNull private final Project myProject;

  public GitHistoryProvider(@NotNull Project project) {
    myProject = project;
  }

  public VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, JComponent forShortcutRegistration) {
    return VcsDependentHistoryComponents.createOnlyColumns(ColumnInfo.EMPTY_ARRAY);
  }

  public AnAction[] getAdditionalActions(Runnable refresher) {
    return new AnAction[] {
      ShowAllAffectedGenericAction.getInstance(),
      ActionManager.getInstance().getAction(VcsActions.ACTION_COPY_REVISION_NUMBER),
      new GitShowCommitInLogAction() };
  }

  public boolean isDateOmittable() {
    return false;
  }

  @Nullable
  public String getHelpId() {
    return null;
  }

  @Override
  public FilePath getUsedFilePath(VcsAbstractHistorySession session) {
    return null;
  }

  @Override
  public Boolean getAddinionallyCachedData(VcsAbstractHistorySession session) {
    return null;
  }

  @Override
  public VcsAbstractHistorySession createFromCachedData(Boolean aBoolean,
                                                        @NotNull List<VcsFileRevision> revisions,
                                                        @NotNull FilePath filePath,
                                                        VcsRevisionNumber currentRevision) {
    return createSession(filePath, revisions, currentRevision);
  }

  @Nullable
  public VcsAbstractHistorySession createSessionFor(final FilePath filePath) throws VcsException {
    List<VcsFileRevision> revisions = null;
    try {
      revisions = GitFileHistory.collectHistory(myProject, filePath);
    } catch (VcsException e) {
      GitVcs.getInstance(myProject).getExecutableValidator().showNotificationOrThrow(e);
    }
    return createSession(filePath, revisions, null);
  }

  private VcsAbstractHistorySession createSession(final FilePath filePath, final List<VcsFileRevision> revisions,
                                                  @Nullable final VcsRevisionNumber number) {
    return new VcsAbstractHistorySession(revisions, number) {
      @Nullable
      protected VcsRevisionNumber calcCurrentRevisionNumber() {
        try {
          return GitHistoryUtils.getCurrentRevision(myProject, filePath, "HEAD");
        }
        catch (VcsException e) {
          // likely the file is not under VCS anymore.
          if (LOG.isDebugEnabled()) {
            LOG.debug("Unable to retrieve the current revision number", e);
          }
          return null;
        }
      }

      public HistoryAsTreeProvider getHistoryAsTreeProvider() {
        return null;
      }

      @Override
      public VcsHistorySession copy() {
        return createSession(filePath, getRevisionList(), getCurrentRevisionNumber());
      }
    };
  }

  @Nullable
  @Override
  public VcsFileRevision getLastRevision(FilePath filePath) throws VcsException {
    List<VcsFileRevision> history = GitFileHistory.collectHistory(myProject, filePath, "--max-count=1");
    if (history.isEmpty()) return null;
    return history.get(0);
  }

  @Override
  public boolean getBaseVersionContent(FilePath filePath, Processor<String> processor, String beforeVersionId) throws VcsException {
    if (StringUtil.isEmptyOrSpaces(beforeVersionId) || filePath.getVirtualFile() == null) return false;
    // apply if base revision id matches revision
    final VirtualFile root = GitUtil.getGitRoot(filePath);
    if (root == null) return false;

    final SHAHash shaHash = GitChangeUtils.commitExists(myProject, root, beforeVersionId, null, "HEAD");
    if (shaHash == null) {
      throw new VcsException("Can not apply patch to " + filePath.getPath() + ".\nCan not find revision '" + beforeVersionId + "'.");
    }

    final ContentRevision content = GitVcs.getInstance(myProject).getDiffProvider()
      .createFileContent(new GitRevisionNumber(shaHash.getValue()), filePath.getVirtualFile());
    if (content == null) {
      throw new VcsException("Can not load content of '" + filePath.getPath() + "' for revision '" + shaHash.getValue() + "'");
    }
    return ! processor.process(content.getContent());
  }

  public void reportAppendableHistory(FilePath path, VcsAppendableHistorySessionPartner partner) {
    reportAppendableHistory(path, null, partner);
  }

  @Override
  public void reportAppendableHistory(@NotNull FilePath path,
                                      @Nullable VcsRevisionNumber startingRevision,
                                      @NotNull final VcsAppendableHistorySessionPartner partner) {
    final VcsAbstractHistorySession emptySession = createSession(path, Collections.emptyList(), null);
    partner.reportCreatedEmptySession(emptySession);

    VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(myProject);
    String[] additionalArgs = vcsConfiguration.LIMIT_HISTORY ?
                              new String[] { "--max-count=" + vcsConfiguration.MAXIMUM_HISTORY_ROWS } :
                              ArrayUtil.EMPTY_STRING_ARRAY;

    final GitExecutableValidator validator = GitVcs.getInstance(myProject).getExecutableValidator();
    GitFileHistory.loadHistory(myProject, refreshPath(path), null, startingRevision,
                           fileRevision -> partner.acceptRevision(fileRevision),
                           exception -> {
                             if (validator.checkExecutableAndNotifyIfNeeded()) {
                               partner.reportException(exception);
                             }
                           },
                               additionalArgs);
  }

  /**
   * Refreshes the IO File inside this FilePath to let it survive moves.
   */
  @NotNull
  private static FilePath refreshPath(@NotNull FilePath path) {
    VirtualFile virtualFile = path.getVirtualFile();
    if (virtualFile == null) {
      return path;
    }
    return VcsUtil.getFilePath(virtualFile);
  }

  public boolean supportsHistoryForDirectories() {
    return true;
  }

  @Override
  public DiffFromHistoryHandler getHistoryDiffHandler() {
    return new GitDiffFromHistoryHandler(myProject);
  }

  @Override
  public boolean canShowHistoryFor(@NotNull VirtualFile file) {
    GitRepositoryManager manager = GitUtil.getRepositoryManager(myProject);
    GitRepository repository = manager.getRepositoryForFileQuick(file);
    return repository != null && !repository.isFresh();
  }

}
