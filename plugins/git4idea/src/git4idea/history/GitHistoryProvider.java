/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitFileRevision;
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
public class GitHistoryProvider implements VcsHistoryProviderEx, VcsCacheableHistorySessionFactory<Boolean, VcsAbstractHistorySession>,
                                           VcsBaseRevisionAdviser {
  private static final Logger log = Logger.getInstance(GitHistoryProvider.class.getName());

  @NotNull private final Project myProject;

  public GitHistoryProvider(@NotNull Project project) {
    myProject = project;
  }

  public VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, JComponent forShortcutRegistration) {
    return VcsDependentHistoryComponents.createOnlyColumns(new ColumnInfo[0]);
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
  public VcsHistorySession createSessionFor(final FilePath filePath) throws VcsException {
    List<VcsFileRevision> revisions = null;
    try {
      revisions = GitHistoryUtils.history(myProject, filePath);
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
          if (log.isDebugEnabled()) {
            log.debug("Unable to retrieve the current revision number", e);
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
    List<VcsFileRevision> history = GitHistoryUtils.history(myProject, filePath, "--max-count=1");
    if (history == null || history.isEmpty()) return null;
    return history.get(0);
  }

  @Override
  public boolean getBaseVersionContent(FilePath filePath,
                                       Processor<CharSequence> processor,
                                       final String beforeVersionId,
                                       List<String> warnings)
    throws VcsException {
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

  public void reportAppendableHistory(FilePath path, VcsAppendableHistorySessionPartner partner) throws VcsException {
    reportAppendableHistory(path, null, partner);
  }

  @Override
  public void reportAppendableHistory(@NotNull FilePath path, 
                                      @Nullable VcsRevisionNumber startingRevision, 
                                      @NotNull final VcsAppendableHistorySessionPartner partner) throws VcsException {
    final VcsAbstractHistorySession emptySession = createSession(path, Collections.<VcsFileRevision>emptyList(), null);
    partner.reportCreatedEmptySession(emptySession);

    VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(myProject);
    String[] additionalArgs = vcsConfiguration.LIMIT_HISTORY ?
                              new String[] { "--max-count=" + vcsConfiguration.MAXIMUM_HISTORY_ROWS } :
                              ArrayUtil.EMPTY_STRING_ARRAY;

    final GitExecutableValidator validator = GitVcs.getInstance(myProject).getExecutableValidator();
    GitHistoryUtils.history(myProject, refreshPath(path), null, startingRevision == null ? GitRevisionNumber.HEAD : startingRevision,
                            new Consumer<GitFileRevision>() {
                              public void consume(GitFileRevision gitFileRevision) {
                                partner.acceptRevision(gitFileRevision);
                              }
                            }, 
                            new Consumer<VcsException>() {
                              public void consume(VcsException e) {
                                if (validator.checkExecutableAndNotifyIfNeeded()) {
                                  partner.reportException(e);
                                }
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
    GitRepository repository = manager.getRepositoryForFile(file);
    return repository != null && !repository.isFresh();
  }

}
