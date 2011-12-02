/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.AsynchConsumer;
import git4idea.GitBranch;
import git4idea.GitTag;
import git4idea.GitVcs;
import git4idea.branch.GitBranchesCollection;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerAdapter;
import git4idea.config.GitConfigUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.history.wholeTree.AbstractHash;
import git4idea.history.wholeTree.CommitHashPlusParents;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.repo.GitRepositoryReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class LowLevelAccessImpl implements LowLevelAccess {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.browser.LowLevelAccessImpl");
  private final Project myProject;
  private final VirtualFile myRoot;

  public LowLevelAccessImpl(final Project project, final VirtualFile root) {
    myProject = project;
    myRoot = root;
  }

  @Override
  public VirtualFile getRoot() {
    return myRoot;
  }

  public void loadHashesWithParents(final @NotNull Collection<String> startingPoints,
                                    @NotNull final Collection<ChangesFilter.Filter> filters,
                                    final AsynchConsumer<CommitHashPlusParents> consumer,
                                    Getter<Boolean> isCanceled, int useMaxCnt, final boolean topoOrder) throws VcsException {
    final List<String> parameters = new ArrayList<String>();
    final Collection<VirtualFile> paths = new HashSet<VirtualFile>();
    ChangesFilter.filtersToParameters(filters, parameters, paths);

    if (! startingPoints.isEmpty()) {
      for (String startingPoint : startingPoints) {
        parameters.add(startingPoint);
      }
    } else {
      parameters.add("--all");
    }
    if (useMaxCnt > 0) {
      parameters.add("--max-count=" + useMaxCnt);
    }
    if (topoOrder) {
      parameters.add("--topo-order");
    } else {
      parameters.add("--date-order");
    }

    GitHistoryUtils.hashesWithParents(myProject, new FilePathImpl(myRoot), consumer, isCanceled, paths, ArrayUtil.toStringArray(parameters));
  }

  @Override
  public List<GitCommit> getCommitDetails(final Collection<String> commitIds, SymbolicRefsI refs) throws VcsException {
    return GitHistoryUtils.commitsDetails(myProject, new FilePathImpl(myRoot), refs, commitIds);
  }

  public void loadCommits(final Collection<String> startingPoints, final Date beforePoint, final Date afterPoint,
                             final Collection<ChangesFilter.Filter> filtersIn, final AsynchConsumer<GitCommit> consumer,
                             int maxCnt, SymbolicRefsI refs, final boolean topoOrder) throws VcsException {
    final Collection<ChangesFilter.Filter> filters = new ArrayList<ChangesFilter.Filter>(filtersIn);
    if (beforePoint != null) {
      filters.add(new ChangesFilter.BeforeDate(new Date(beforePoint.getTime() - 1)));
    }
    if (afterPoint != null) {
      filters.add(new ChangesFilter.AfterDate(afterPoint));
    }

    loadCommits(startingPoints, Collections.<String>emptyList(), filters, consumer, maxCnt, null, refs, topoOrder);
  }

  // uses cached version
  public CachedRefs getRefs() throws VcsException {
    final CachedRefs refs = new CachedRefs();
    final GitRepository repositoryForRoot = GitRepositoryManager.getInstance(myProject).getRepositoryForRoot(myRoot);
    final GitBranchesCollection branches;
    if (repositoryForRoot != null) {
      branches = repositoryForRoot.getBranches();
    } else {
      final File child = new File(myRoot.getPath(), ".git");
      if (! child.exists()) {
        throw new VcsException("No git repository in " + myRoot.getPath());
      }
      final GitRepositoryReader reader = new GitRepositoryReader(child);
      branches = reader.readBranches();
    }
    refs.setCollection(branches);
    final GitBranch current = branches.getCurrentBranch();
    if (current != null) {
      GitBranch tracked = current.tracked(myProject, myRoot);
      String fullName = tracked == null ? null : tracked.getFullName();
      fullName = fullName != null && fullName.startsWith(GitBranch.REFS_REMOTES_PREFIX) ? fullName.substring(GitBranch.REFS_REMOTES_PREFIX.length()) : fullName;
      refs.setTrackedRemoteName(fullName);
    }
    refs.setUsername(GitConfigUtil.getValue(myProject, myRoot, GitConfigUtil.USER_NAME));
    final VcsRevisionNumber head = GitHistoryUtils.getCurrentRevision(myProject, new FilePathImpl(myRoot), "HEAD", true);
    refs.setHeadHash(AbstractHash.create(head.asString()));
    return refs;
  }

  public void loadCommits(final @NotNull Collection<String> startingPoints, @NotNull final Collection<String> endPoints,
                          @NotNull final Collection<ChangesFilter.Filter> filters,
                          @NotNull final AsynchConsumer<GitCommit> consumer,
                          int useMaxCnt,
                          Getter<Boolean> isCanceled, SymbolicRefsI refs, final boolean topoOrder)
    throws VcsException {

    final List<String> parameters = new ArrayList<String>();
    if (useMaxCnt > 0) {
      parameters.add("--max-count=" + useMaxCnt);
    }

    final Collection<VirtualFile> paths = new HashSet<VirtualFile>();
    ChangesFilter.filtersToParameters(filters, parameters, paths);

    if (! startingPoints.isEmpty()) {
      for (String startingPoint : startingPoints) {
        parameters.add(startingPoint);
      }
    } else {
      parameters.add("--all");
    }
    if (topoOrder) {
      parameters.add("--topo-order");
    } else {
      parameters.add("--date-order");
    }

    for (String endPoint : endPoints) {
      parameters.add("^" + endPoint);
    }

    GitHistoryUtils.historyWithLinks(myProject, new FilePathImpl(myRoot),
                                     refs, consumer, isCanceled, paths, ArrayUtil.toStringArray(parameters));
  }

  public List<String> getBranchesWithCommit(final SHAHash hash) throws VcsException {
    return getBranchesWithCommit(hash.getValue());
  }

  public List<String> getBranchesWithCommit(final String hash) throws VcsException {
    final List<String> result = new ArrayList<String>();
    GitBranch.listAsStrings(myProject, myRoot, true, true, result, hash);
    //GitBranch.listAsStrings(myProject, myRoot, true, false, result, hash.getValue());
    return result;
  }

  public Collection<String> getTagsWithCommit(final SHAHash hash) throws VcsException {
    final List<String> result = new ArrayList<String>();
    GitTag.listAsStrings(myProject, myRoot, result, hash.getValue());
    return result;
  }

  @Nullable
  public GitBranch loadLocalBranches(Collection<String> sink) throws VcsException {
    return GitBranch.listAsStrings(myProject, myRoot, false, true, sink, null);
  }

  @Nullable
  public GitBranch loadRemoteBranches(Collection<String> sink) throws VcsException {
    return GitBranch.listAsStrings(myProject, myRoot, true, false, sink, null);
  }

  public void loadAllBranches(List<String> sink) throws VcsException {
    GitBranch.listAsStrings(myProject, myRoot, true, false, sink, null);
    GitBranch.listAsStrings(myProject, myRoot, false, true, sink, null);
  }

  public void loadAllTags(Collection<String> sink) throws VcsException {
    GitTag.listAsStrings(myProject, myRoot, sink, null);
  }

  public boolean cherryPick(GitCommit commit) throws VcsException {
    final GitLineHandler handler = new GitLineHandler(myProject, myRoot, GitCommand.CHERRY_PICK);
    handler.addParameters("-x", "-n", commit.getHash().getValue());
    handler.endOptions();
    handler.setNoSSH(true);

    final AtomicBoolean conflict = new AtomicBoolean();

    handler.addLineListener(new GitLineHandlerAdapter() {
      public void onLineAvailable(String line, Key outputType) {
        if (line.toLowerCase().contains("after resolving the conflicts")) {
          conflict.set(true);
        }
      }
    });
    handler.runInCurrentThread(null);

    if (conflict.get()) {
      return new CherryPickConflictResolver(myProject, myRoot, commit.getShortHash().getString(), commit.getAuthor(), commit.getSubject()).merge();
    } else {
      final List<VcsException> errors = handler.errors();
      if (!errors.isEmpty()) {
        throw errors.get(0);
      } else { // no conflicts, no errors
        return true;
      }
    }
  }

  private static class CherryPickConflictResolver extends GitConflictResolver {

    private VirtualFile myRoot;
    private String myCommitHash;
    private String myCommitAuthor;
    private String myCommitMessage;

    public CherryPickConflictResolver(Project project, VirtualFile root, String commitHash, String commitAuthor, String commitMessage) {
      super(project, Collections.singleton(root), makeParams(commitHash, commitAuthor, commitMessage));
      myRoot = root;
      myCommitHash = commitHash;
      myCommitAuthor = commitAuthor;
      myCommitMessage = commitMessage;
    }
    
    private static Params makeParams(String commitHash, String commitAuthor, String commitMessage) {
      Params params = new Params();
      params.setErrorNotificationTitle("Cherry-picked with conflicts");
      params.setMergeDialogCustomizer(new CherryPickMergeDialogCustomizer(commitHash, commitAuthor, commitMessage));
      return params;
    }

    @Override
    protected void notifyUnresolvedRemain() {
      GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Conflicts were not resolved during cherry-pick",
                                                "Cherry-pick is not complete, you have unresolved merges in your working tree<br/>" +
                                                "<a href='resolve'>Resolve</a> conflicts.",
                                                NotificationType.WARNING, new NotificationListener() {
          @Override
          public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              if (event.getDescription().equals("resolve")) {
                new CherryPickConflictResolver(myProject, myRoot, myCommitHash, myCommitAuthor, myCommitMessage).mergeNoProceed();
              }
            }
          }
      }).notify(myProject);
    }
  }

  private static class CherryPickMergeDialogCustomizer extends MergeDialogCustomizer {

    private String myCommitHash;
    private String myCommitAuthor;
    private String myCommitMessage;

    public CherryPickMergeDialogCustomizer(String commitHash, String commitAuthor, String commitMessage) {
      myCommitHash = commitHash;
      myCommitAuthor = commitAuthor;
      myCommitMessage = commitMessage;
    }

    @Override
    public String getMultipleFileMergeDescription(Collection<VirtualFile> files) {
      return "<html>Conflicts during cherry-picking commit <code>" + myCommitHash + "</code> made by " + myCommitAuthor + "<br/>" +
             "<code>\"" + myCommitMessage + "\"</code></html>";
    }

    @Override
    public String getLeftPanelTitle(VirtualFile file) {
      return "Local changes";
    }

    @Override
    public String getRightPanelTitle(VirtualFile file, VcsRevisionNumber lastRevisionNumber) {
      return "<html>Changes from cherry-pick <code>" + myCommitHash + "</code>";
    }
  }

}
