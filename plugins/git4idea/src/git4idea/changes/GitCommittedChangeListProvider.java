/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedListsZipper;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedViewAuxiliary;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.Consumer;
import git4idea.GitBranch;
import git4idea.GitFileRevision;
import git4idea.GitRemote;
import git4idea.GitUtil;
import git4idea.commands.GitSimpleHandler;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.SymbolicRefs;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * The provider for committed change lists
 */
public class GitCommittedChangeListProvider implements CommittedChangesProvider<CommittedChangeList, ChangeBrowserSettings> {
  /**
   * the logger
   */
  private static final Logger LOG = Logger.getInstance(GitCommittedChangeListProvider.class.getName());


  /**
   * The project for the service
   */
  private final Project myProject;

  /**
   * The constructor
   *
   * @param project the project instance for this provider
   */
  public GitCommittedChangeListProvider(Project project) {
    myProject = project;
  }

  /**
   * {@inheritDoc}
   */
  public ChangeBrowserSettings createDefaultSettings() {
    return new ChangeBrowserSettings();
  }

  /**
   * {@inheritDoc}
   */
  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(boolean showDateFilter) {
    return new GitVersionFilterComponent(showDateFilter);
  }

  /**
   * {@inheritDoc}
   */
  public RepositoryLocation getLocationFor(FilePath root) {
    // TODO !!! consider some caching for the case when we do not have tracked remote branch
    // TODO - in this case returned NULL is NOT cached (in ProjectKeyComponent)
    VirtualFile gitRoot = GitUtil.getGitRootOrNull(root);
    if (gitRoot == null) {
      return null;
    }
    try {
      GitBranch c = GitBranch.current(myProject, gitRoot);
      if (c == null) {
        return null;
      }
      String remote = c.getTrackedRemoteName(myProject, gitRoot);
      if (StringUtil.isEmpty(remote)) {
        return null;
      }
      File rootFile = new File(gitRoot.getPath());
      if (".".equals(remote)) {
        return new GitRepositoryLocation(gitRoot.getUrl(), rootFile);
      }
      else {
        GitRemote r = GitRemote.find(myProject, gitRoot, remote);
        return r == null ? null : new GitRepositoryLocation(r.fetchUrl(), rootFile);
      }
    }
    catch (VcsException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Exception for determining repository location", e);
      }
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  public RepositoryLocation getLocationFor(FilePath root, String repositoryPath) {
    return getLocationFor(root);
  }

  /**
   * {@inheritDoc}
   */
  public VcsCommittedListsZipper getZipper() {
    return null;
  }

  public void loadCommittedChanges(ChangeBrowserSettings settings,
                                   RepositoryLocation location,
                                   int maxCount,
                                   AsynchConsumer<CommittedChangeList> consumer)
    throws VcsException {
    try {
      getCommittedChangesImpl(settings, location, maxCount, consumer);
    }
    finally {
      consumer.finished();
    }
  }

  /**
   * {@inheritDoc}
   */
  public List<CommittedChangeList> getCommittedChanges(ChangeBrowserSettings settings, RepositoryLocation location, final int maxCount)
    throws VcsException {

    final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();

    getCommittedChangesImpl(settings, location, maxCount, new Consumer<CommittedChangeList>() {
      public void consume(CommittedChangeList committedChangeList) {
        result.add(committedChangeList);
      }
    });

    return result;
  }

  private void getCommittedChangesImpl(ChangeBrowserSettings settings, RepositoryLocation location, final int maxCount,
                                                            final Consumer<CommittedChangeList> consumer)
    throws VcsException {
    GitRepositoryLocation l = (GitRepositoryLocation)location;
    final Long beforeRev = settings.getChangeBeforeFilter();
    final Long afterRev = settings.getChangeBeforeFilter();
    final Date beforeDate = settings.getDateBeforeFilter();
    final Date afterDate = settings.getDateBeforeFilter();
    final String author = settings.getUserFilter();
    VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(l.getRoot());
    if (root == null) {
      throw new VcsException("The repository does not exists anymore: " + l.getRoot());
    }

    GitUtil.getLocalCommittedChanges(myProject, root, new Consumer<GitSimpleHandler>() {
      public void consume(GitSimpleHandler h) {
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
      }
    }, consumer, false);
  }

  /**
   * {@inheritDoc}
   */
  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[]{ChangeListColumn.NUMBER, ChangeListColumn.DATE, ChangeListColumn.DESCRIPTION, ChangeListColumn.NAME};
  }

  /**
   * {@inheritDoc}
   */
  public VcsCommittedViewAuxiliary createActions(DecoratorManager manager, RepositoryLocation location) {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public int getUnlimitedCountValue() {
    return -1;
  }

  @Override
  public Pair<CommittedChangeList, FilePath> getOneList(final VirtualFile file, final VcsRevisionNumber number) throws VcsException {
    final FilePathImpl filePath = new FilePathImpl(file);

    final List<GitCommit> gitCommits =
      GitHistoryUtils.commitsDetails(myProject, filePath, new SymbolicRefs(), Collections.singletonList(number.asString()));
    if (gitCommits == null || gitCommits.size() != 1) return null;
    final GitCommit gitCommit = gitCommits.get(0);
    final CommittedChangeList commit = new CommittedChangeListImpl(gitCommit.getDescription() + " (" + gitCommit.getShortHash().getString() + ")",
                                                                   gitCommit.getDescription(), gitCommit.getCommitter(),
                                                                   GitChangeUtils.longForSHAHash(gitCommit.getHash().getValue()),
                                                                   gitCommit.getDate(), gitCommit.getChanges());

    final Collection<Change> changes = commit.getChanges();
    if (changes.size() == 1) {
      return new Pair<CommittedChangeList, FilePath>(commit, changes.iterator().next().getAfterRevision().getFile());
    }
    for (Change change : changes) {
      if (change.getAfterRevision() != null && filePath.getIOFile().equals(change.getAfterRevision().getFile().getIOFile())) {
        return new Pair<CommittedChangeList, FilePath>(commit, filePath);
      }
    }
    final List<VcsFileRevision> history = GitHistoryUtils.history(myProject, filePath, null, number.asString() + "..");
    return new Pair<CommittedChangeList, FilePath>(commit, ((GitFileRevision) history.get(history.size() - 1)).getPath());
  }

  public int getFormatVersion() {
    return 0;
  }

  public void writeChangeList(DataOutput stream, CommittedChangeList list) throws IOException {
  }

  public CommittedChangeList readChangeList(RepositoryLocation location, DataInput stream) throws IOException {
    return null;
  }

  public boolean isMaxCountSupported() {
    return false;
  }

  public Collection<FilePath> getIncomingFiles(RepositoryLocation location) throws VcsException {
    return null;
  }

  public boolean refreshCacheByNumber() {
    return false;
  }

  @Nls
  public String getChangelistTitle() {
    return null;
  }

  public boolean isChangeLocallyAvailable(FilePath filePath,
                                          @Nullable VcsRevisionNumber localRevision,
                                          VcsRevisionNumber changeRevision,
                                          CommittedChangeList changeList) {
    return false;
  }

  public boolean refreshIncomingWithCommitted() {
    return false;
  }
}
