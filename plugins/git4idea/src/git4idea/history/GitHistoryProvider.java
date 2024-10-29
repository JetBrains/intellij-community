// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcs.log.ui.actions.ShowCommitInLogAction;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitObjectType;
import git4idea.index.GitIndexUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Git history provider implementation
 */
@Service(Service.Level.PROJECT)
public final class GitHistoryProvider implements VcsHistoryProviderEx,
                                                 VcsCacheableHistorySessionFactory<Boolean, VcsAbstractHistorySession>,
                                                 VcsBaseRevisionAdviser {
  private static final Logger LOG = Logger.getInstance(GitHistoryProvider.class.getName());

  private final @NotNull Project myProject;

  public GitHistoryProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, JComponent forShortcutRegistration) {
    return VcsDependentHistoryComponents.createOnlyColumns(ColumnInfo.EMPTY_ARRAY);
  }

  @Override
  public AnAction[] getAdditionalActions(Runnable refresher) {
    return new AnAction[]{
      ShowAllAffectedGenericAction.getInstance(),
      ActionManager.getInstance().getAction(VcsActions.ACTION_COPY_REVISION_NUMBER),
      new ShowCommitInLogAction()};
  }

  @Override
  public boolean isDateOmittable() {
    return false;
  }

  @Override
  public @Nullable String getHelpId() {
    return null;
  }

  @Override
  public VcsAbstractHistorySession createFromCachedData(Boolean aBoolean,
                                                        @NotNull List<? extends VcsFileRevision> revisions,
                                                        @NotNull FilePath filePath,
                                                        VcsRevisionNumber currentRevision) {
    return createSession(filePath, revisions, currentRevision);
  }

  @Override
  public @NotNull VcsAbstractHistorySession createSessionFor(final FilePath filePath) throws VcsException {
    List<VcsFileRevision> revisions = GitFileHistory.collectHistory(myProject, filePath);
    return createSession(filePath, revisions, revisions.isEmpty() ? null : getFirstItem(revisions).getRevisionNumber());
  }

  private VcsAbstractHistorySession createSession(final FilePath filePath, final List<? extends VcsFileRevision> revisions,
                                                  final @Nullable VcsRevisionNumber number) {
    return new GitHistorySession(filePath, number, revisions);
  }

  @Override
  public @Nullable VcsFileRevision getLastRevision(FilePath filePath) throws VcsException {
    List<VcsFileRevision> history = GitFileHistory.collectHistory(myProject, filePath, "--max-count=1");
    if (history.isEmpty()) return null;
    return history.get(0);
  }

  @Override
  public @Nullable String getBaseVersionContent(@NotNull FilePath filePath, @NotNull String beforeVersionId) throws VcsException {
    if (StringUtil.isEmptyOrSpaces(beforeVersionId) || filePath.getVirtualFile() == null) return null;
    if (!GitUtil.isHashString(beforeVersionId, false)) return null;

    // apply if base revision id matches revision
    GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(filePath);
    if (repository == null) return null;

    GitObjectType objectType = Git.getInstance().getObjectTypeEnum(repository, beforeVersionId);
    return objectType == null ? null : switch (objectType) {
      case COMMIT -> GitContentRevision.createRevision(filePath, new GitRevisionNumber(beforeVersionId), myProject).getContent();
      case BLOB -> ContentRevisionCache.getAsString(GitIndexUtil.read(repository, beforeVersionId), filePath, null);
      case TREE -> null;
    };
  }

  @Override
  public void reportAppendableHistory(FilePath path, VcsAppendableHistorySessionPartner partner) {
    reportAppendableHistory(path, null, partner);
  }

  @Override
  public void reportAppendableHistory(@NotNull FilePath path,
                                      @Nullable VcsRevisionNumber startingRevision,
                                      final @NotNull VcsAppendableHistorySessionPartner partner) {
    final VcsAbstractHistorySession emptySession = createSession(path, Collections.emptyList(), null);
    partner.reportCreatedEmptySession(emptySession);

    String[] additionalArgs = getHistoryLimitArgs(myProject);

    GitFileHistory.loadHistory(myProject, path, startingRevision,
                               fileRevision -> partner.acceptRevision(fileRevision),
                               exception -> partner.reportException(exception),
                               rename -> { },
                               additionalArgs);
  }

  @Override
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

  static String @NotNull [] getHistoryLimitArgs(@NotNull Project project) {
    VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(project);
    return vcsConfiguration.LIMIT_HISTORY ?
           new String[]{"--max-count=" + vcsConfiguration.MAXIMUM_HISTORY_ROWS} :
           ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  final class GitHistorySession extends VcsAbstractHistorySession {
    private final @NotNull FilePath myFilePath;

    GitHistorySession(@NotNull FilePath filePath, @Nullable VcsRevisionNumber number, @NotNull List<? extends VcsFileRevision> revisions) {
      super(revisions, number);
      myFilePath = filePath;
    }

    @Override
    protected @Nullable VcsRevisionNumber calcCurrentRevisionNumber() {
      try {
        return GitHistoryUtils.getCurrentRevision(myProject, myFilePath, "HEAD");
      }
      catch (VcsException e) {
        // likely the file is not under VCS anymore.
        if (LOG.isDebugEnabled()) {
          LOG.debug("Unable to retrieve the current revision number", e);
        }
        return null;
      }
    }

    @Override
    public VcsHistorySession copy() {
      return createSession(myFilePath, getRevisionList(), getCurrentRevisionNumber());
    }

    @NotNull FilePath getFilePath() {
      return myFilePath;
    }
  }
}
