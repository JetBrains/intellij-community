// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedListsZipper;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedViewAuxiliary;
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
import git4idea.history.GitLogUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * The provider for committed change lists
 */
public class GitCommittedChangeListProvider implements CommittedChangesProvider<CommittedChangeList, ChangeBrowserSettings> {

  private static final Logger LOG = Logger.getInstance(GitCommittedChangeListProvider.class);

  @NotNull private final Project myProject;

  public GitCommittedChangeListProvider(@NotNull Project project) {
    myProject = project;
  }

  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(boolean showDateFilter) {
    return new GitVersionFilterComponent(showDateFilter);
  }

  public RepositoryLocation getLocationFor(@NotNull FilePath root) {
    VirtualFile gitRoot = GitUtil.getGitRootOrNull(root);
    if (gitRoot == null) {
      return null;
    }
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

  @Nullable
  public VcsCommittedListsZipper getZipper() {
    return null;
  }

  public void loadCommittedChanges(ChangeBrowserSettings settings, RepositoryLocation location, int maxCount,
                                   final AsynchConsumer<CommittedChangeList> consumer) throws VcsException {
    try {
      getCommittedChangesImpl(settings, location, maxCount, gitCommittedChangeList -> consumer.consume(gitCommittedChangeList));
    }
    finally {
      consumer.finished();
    }
  }

  public List<CommittedChangeList> getCommittedChanges(ChangeBrowserSettings settings, RepositoryLocation location, final int maxCount)
    throws VcsException {

    final List<CommittedChangeList> result = new ArrayList<>();

    getCommittedChangesImpl(settings, location, maxCount, committedChangeList -> result.add(committedChangeList));

    return result;
  }

  private void getCommittedChangesImpl(ChangeBrowserSettings settings, RepositoryLocation location, final int maxCount,
                                       final Consumer<GitCommittedChangeList> consumer)
    throws VcsException {
    GitRepositoryLocation l = (GitRepositoryLocation)location;
    final Long beforeRev = settings.getChangeBeforeFilter();
    final Long afterRev = settings.getChangeAfterFilter();
    final Date beforeDate = settings.getDateBeforeFilter();
    final Date afterDate = settings.getDateAfterFilter();
    final String author = settings.getUserFilter();
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(l.getRoot());
    if (root == null) {
      throw new VcsException("The repository does not exists anymore: " + l.getRoot());
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

  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[]{ChangeListColumn.NUMBER, ChangeListColumn.DATE, ChangeListColumn.DESCRIPTION, ChangeListColumn.NAME};
  }

  public VcsCommittedViewAuxiliary createActions(DecoratorManager manager, RepositoryLocation location) {
    return null;
  }

  public int getUnlimitedCountValue() {
    return -1;
  }

  @Override
  public Pair<CommittedChangeList, FilePath> getOneList(@NotNull VirtualFile file, @NotNull VcsRevisionNumber number)
    throws VcsException {
    FilePath filePath = VcsUtil.getFilePath(file);

    GitRepository repository =
      GitRepositoryManager.getInstance(myProject).getRepositoryForFile(GitHistoryUtils.getLastCommitName(myProject, filePath));
    if (repository == null) {
      return null;
    }
    VirtualFile root = repository.getRoot();

    String[] hashParameters = GitHistoryUtils.formHashParameters(repository.getVcs(), Collections.singleton(number.asString()));
    List<GitCommit> gitCommits = GitLogUtil.collectFullDetails(myProject, root, hashParameters);
    if (gitCommits.size() != 1) {
      return null;
    }
    VcsFullCommitDetails gitCommit = gitCommits.get(0);
    CommittedChangeList commit = new GitCommittedChangeList(gitCommit.getFullMessage() + " (" + gitCommit.getId().toShortString() + ")",
                                                            gitCommit.getFullMessage(), VcsUserUtil.toExactString(gitCommit.getAuthor()),
                                                            (GitRevisionNumber)number,
                                                            new Date(gitCommit.getAuthorTime()), gitCommit.getChanges(),
                                                            GitVcs.getInstance(myProject), true);

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

  @Override
  public RepositoryLocation getForNonLocal(VirtualFile file) {
    return null;
  }

  @Override
  public boolean supportsIncomingChanges() {
    return false;
  }
}
