// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.history.GitFileHistory;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * The provider for committed change lists
 */
@Service(Service.Level.PROJECT)
public final class GitCommittedChangeListProvider implements CommittedChangesProvider<CommittedChangeList, ChangeBrowserSettings> {
  private static final Logger LOG = Logger.getInstance(GitCommittedChangeListProvider.class);

  @NotNull private final Project myProject;

  public GitCommittedChangeListProvider(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(boolean showDateFilter) {
    return new GitVersionFilterComponent(showDateFilter);
  }

  @Nullable
  @Override
  public RepositoryLocation getLocationFor(@NotNull FilePath rootPath) {
    VirtualFile gitRoot = rootPath.getVirtualFile();
    if (gitRoot == null) return null;

    GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(gitRoot);
    if (repository == null) {
      LOG.info("No GitRepository for " + gitRoot);
      return null;
    }
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch == null) {
      return null;
    }
    GitRemoteBranch trackedBranch = currentBranch.findTrackedBranch(repository);
    if (trackedBranch == null) {
      return null;
    }
    File rootFile = new File(gitRoot.getPath());
    return new GitRepositoryLocation(trackedBranch.getRemote().getFirstUrl(), rootFile);
  }

  @Override
  public void loadCommittedChanges(@NotNull ChangeBrowserSettings settings,
                                   @NotNull RepositoryLocation location,
                                   int maxCount,
                                   @NotNull AsynchConsumer<? super CommittedChangeList> consumer) throws VcsException {
    try {
      getCommittedChangesImpl(settings, location, maxCount, consumer);
    }
    finally {
      consumer.finished();
    }
  }

  @NotNull
  @Override
  public List<CommittedChangeList> getCommittedChanges(@NotNull ChangeBrowserSettings settings,
                                                       @NotNull RepositoryLocation location,
                                                       int maxCount)
    throws VcsException {
    List<CommittedChangeList> result = new ArrayList<>();
    getCommittedChangesImpl(settings, location, maxCount, committedChangeList -> result.add(committedChangeList));
    return result;
  }

  private void getCommittedChangesImpl(@NotNull ChangeBrowserSettings settings, @NotNull RepositoryLocation location, final int maxCount,
                                       final Consumer<? super GitCommittedChangeList> consumer)
    throws VcsException {
    GitRepositoryLocation l = (GitRepositoryLocation)location;
    final Long beforeRev = settings.getChangeBeforeFilter();
    final Long afterRev = settings.getChangeAfterFilter();
    final Date beforeDate = settings.getDateBeforeFilter();
    final Date afterDate = settings.getDateAfterFilter();
    final String author = settings.getUserFilter();
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(l.getRoot());
    if (root == null) {
      throw new VcsException(GitBundle.message("error.git.repository.not.found", l.getRoot()));
    }

    GitUtil.getLocalCommittedChanges(myProject, root, h -> {
      if (!StringUtil.isEmpty(author)) {
        h.addParameters("--author=" + author);
      }
      if (beforeDate != null) {
        h.addParameters("--before=" + GitUtil.gitTime(beforeDate));
      }
      if (afterDate != null) {
        h.addParameters("--after=" + GitUtil.gitTime(afterDate));
      }
      if (maxCount != getUnlimitedCountValue()) {
        h.addParameters("-n" + maxCount);
      }
      if (beforeRev != null && afterRev != null) {
        h.addParameters(GitUtil.formatLongRev(afterRev) + ".." + GitUtil.formatLongRev(beforeRev));
      }
      else if (beforeRev != null) {
        h.addParameters(GitUtil.formatLongRev(beforeRev));
      }
      else if (afterRev != null) {
        h.addParameters(GitUtil.formatLongRev(afterRev) + "..");
      }
    }, consumer, false);
  }

  @Override
  public ChangeListColumn @NotNull [] getColumns() {
    return new ChangeListColumn[]{ChangeListColumn.NUMBER, ChangeListColumn.DATE, ChangeListColumn.DESCRIPTION, ChangeListColumn.NAME};
  }

  @Override
  public int getUnlimitedCountValue() {
    return -1;
  }

  @Nullable
  @Override
  public Pair<CommittedChangeList, FilePath> getOneList(@NotNull VirtualFile file, @NotNull VcsRevisionNumber number)
    throws VcsException {
    FilePath filePath = VcsUtil.getFilePath(file);
    if (!(number instanceof GitRevisionNumber)) {
      LOG.error("Unsupported revision number: " + number);
      return null;
    }

    GitRepository repository =
      GitRepositoryManager.getInstance(myProject).getRepositoryForFile(VcsUtil.getLastCommitPath(myProject, filePath));
    if (repository == null) {
      return null;
    }
    VirtualFile root = repository.getRoot();

    VcsFullCommitDetails gitCommit = getCommitDetails(myProject, root, number);
    if (gitCommit == null) return null;

    GitCommittedChangeList commit = createCommittedChangeList(myProject, gitCommit, (GitRevisionNumber)number);
    Collection<Change> changes = commit.getChanges();

    if (changes.size() == 1) {
      Change change = changes.iterator().next();
      return Pair.create(commit, ChangesUtil.getFilePath(change));
    }
    for (Change change : changes) {
      if (change.getAfterRevision() != null && FileUtil.filesEqual(filePath.getIOFile(), change.getAfterRevision().getFile().getIOFile())) {
        return Pair.create(commit, filePath);
      }
    }
    String afterTime = "--after=" + GitUtil.gitTime(new Date(gitCommit.getCommitTime()));
    List<VcsFileRevision> history = GitFileHistory.collectHistory(myProject, filePath, afterTime);
    if (history.isEmpty()) {
      return Pair.create(commit, filePath);
    }
    return Pair.create(commit, ((GitFileRevision)history.get(history.size() - 1)).getPath());
  }

  @Nullable
  private static VcsFullCommitDetails getCommitDetails(@NotNull Project project,
                                                       @NotNull VirtualFile root,
                                                       @NotNull VcsRevisionNumber number) throws VcsException {
    String[] hashParameters = GitHistoryUtils.formHashParameters(project, Collections.singleton(number.asString()));
    List<GitCommit> gitCommits = GitHistoryUtils.history(project, root, hashParameters);
    if (gitCommits.size() != 1) return null;

    return gitCommits.get(0);
  }

  @NotNull
  private static GitCommittedChangeList createCommittedChangeList(@NotNull Project project,
                                                                  @NotNull VcsFullCommitDetails gitCommit,
                                                                  @NotNull GitRevisionNumber revisionNumber) {
    return new GitCommittedChangeList(gitCommit.getFullMessage() + " (" + gitCommit.getId().toShortString() + ")",
                                      gitCommit.getFullMessage(), VcsUserUtil.toExactString(gitCommit.getAuthor()),
                                      revisionNumber,
                                      new Date(gitCommit.getAuthorTime()), gitCommit.getChanges(),
                                      GitVcs.getInstance(project), true);
  }

  @Nullable
  public static GitCommittedChangeList getCommittedChangeList(@NotNull Project project,
                                                              @NotNull VirtualFile root,
                                                              @NotNull GitRevisionNumber revisionNumber) throws VcsException {
    VcsFullCommitDetails details = getCommitDetails(project, root, revisionNumber);
    if (details == null) return null;

    return createCommittedChangeList(project, details, revisionNumber);
  }

  @Override
  public boolean supportsIncomingChanges() {
    return false;
  }
}
