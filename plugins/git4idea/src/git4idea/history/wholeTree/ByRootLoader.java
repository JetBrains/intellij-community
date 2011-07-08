/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import git4idea.changes.GitChangeUtils;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author irengrig
 *         Date: 2/1/11
 *         Time: 6:30 PM
 *
 *  We wouldn't include it into growth controller (not many rows loaded)
 */
public class ByRootLoader extends TaskDescriptor {
  private final Project myProject;
  private final LoaderAndRefresherImpl.MyRootHolder myRootHolder;
  private final LowLevelAccess myLowLevelAccess;
  private final Mediator myMediator;
  private final DetailsCache myDetailsCache;
  private SymbolicRefs mySymbolicRefs;
  private final Mediator.Ticket myTicket;
  private final UsersIndex myUsersIndex;
  private final Collection<String> myStartingPoints;
  @NotNull
  private final GitLogFilters myGitLogFilters;

  public ByRootLoader(Project project,
                      LoaderAndRefresherImpl.MyRootHolder rootHolder,
                      Mediator mediator,
                      DetailsCache detailsCache,
                      Mediator.Ticket ticket, UsersIndex usersIndex, GitLogFilters gitLogFilters, final Collection<String> startingPoints) {
    super("Initial checks", Where.POOLED);
    myProject = project;
    myRootHolder = rootHolder;
    myUsersIndex = usersIndex;
    myStartingPoints = startingPoints;
    myLowLevelAccess = new LowLevelAccessImpl(myProject, myRootHolder.getRoot());
    myMediator = mediator;
    myDetailsCache = detailsCache;
    myTicket = ticket;
    myGitLogFilters = gitLogFilters;
  }

  @Override
  public void run(ContinuationContext context) {
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
    progress(pi, "Load branches and tags");
    initSymbRefs();
    progress(pi, "Load stashed");
    loadStash();
    progress(pi, "Try to load by reference");
    loadByHashesAside(context);
  }

  private void progress(final ProgressIndicator pi, final String progress) {
    if (pi != null) {
      pi.checkCanceled();
      pi.setText(progress);
    }
  }

  private void loadStash() {
    // start is not on a branch
    if (myStartingPoints != null && (! myStartingPoints.isEmpty())) return;

    final List<GitCommit> details = new ArrayList<GitCommit>();
    final List<CommitI> commits = new ArrayList<CommitI>();
    final Map<AbstractHash, String> stashMap = new HashMap<AbstractHash, String>();
    final List<List<AbstractHash>> parents = myGitLogFilters.isEmpty() ? new ArrayList<List<AbstractHash>>() : null;

    myGitLogFilters.callConsumer(new Consumer<List<ChangesFilter.Filter>>() {
      @Override
      public void consume(List<ChangesFilter.Filter> filters) {
        ProgressManager.checkCanceled();
        try {
          final List<String> parameters = new ArrayList<String>();
          final List<VirtualFile> paths = new ArrayList<VirtualFile>();
          ChangesFilter.filtersToParameters(filters, parameters, paths);
          final List<Pair<String,GitCommit>> stash = GitHistoryUtils.loadStashStackAsCommits(myProject, myRootHolder.getRoot(),
                                                                             mySymbolicRefs, parameters.toArray(new String[parameters.size()]));
          if (stash == null) return;
          for (Pair<String, GitCommit> pair : stash) {
            ProgressManager.checkCanceled();
            final GitCommit gitCommit = pair.getSecond();
            if (stashMap.containsKey(gitCommit.getShortHash())) continue;

            details.add(gitCommit);
            if (parents != null) {
              parents.add(gitCommit.getConvertedParents());
            }
            commits.add(createCommitI(gitCommit));
            stashMap.put(gitCommit.getShortHash(), pair.getFirst());
          }
        }
        catch (VcsException e) {
          myMediator.acceptException(e);
        }
      }
    }, true, myRootHolder.getRoot());

    myDetailsCache.putStash(myRootHolder.getRoot(), stashMap);
    ProgressManager.checkCanceled();
    // does not work
    //myDetailsCache.acceptAnswer(details, myRootHolder.getRoot());
    myMediator.appendResult(myTicket, commits, parents);
  }

  // if there're filters -> parents shouldn't be loaded
  public void loadByHashesAside(final ContinuationContext context) {
    final List<CommitI> result = new ArrayList<CommitI>();
    final Set<SHAHash> controlSet = new HashSet<SHAHash>();

    final List<String> hashes = myGitLogFilters.getPossibleReferencies();
    if (hashes == null) return;
    myGitLogFilters.callConsumer(new Consumer<List<ChangesFilter.Filter>>() {
      @Override
      public void consume(List<ChangesFilter.Filter> filters) {
        for (String hash : hashes) {
          try {
            final List<String> parameters = new ArrayList<String>();
            final List<VirtualFile> paths = new ArrayList<VirtualFile>();
            ChangesFilter.filtersToParameters(filters, parameters, paths);
            final SHAHash shaHash = GitChangeUtils.commitExists(myProject, myRootHolder.getRoot(), hash, paths,
                                                                parameters.toArray(new String[parameters.size()]));
            if (shaHash == null) continue;
            if (controlSet.contains(shaHash)) continue;
            controlSet.add(shaHash);

            if (myStartingPoints != null && (! myStartingPoints.isEmpty())) {
              boolean matches = false;
              for (String startingPoint : myStartingPoints) {
                if(GitChangeUtils.isAnyLevelChild(myProject, myRootHolder.getRoot(), shaHash, startingPoint)) {
                  matches = true;
                  break;
                }
              }
              if (! matches) continue;
            }
            final List<GitCommit> commits = myLowLevelAccess.getCommitDetails(Collections.singletonList(shaHash.getValue()), mySymbolicRefs);
            if (commits.isEmpty()) continue;

            myDetailsCache.acceptAnswer(commits, myRootHolder.getRoot());
            appendCommits(result, commits);
          }
          catch (VcsException e1) {
            continue;
          }
        }
      }
    }, false, myRootHolder.getRoot());

    if (! result.isEmpty()) {
      final StepType stepType = myMediator.appendResult(myTicket, result, null);
      // here we react only on "stop", not on "pause"
      if (StepType.STOP.equals(stepType)) {
        context.cancelEverything();
      }
    }
  }

  private void appendCommits(List<CommitI> result, List<GitCommit> commits) {
    for (GitCommit commit : commits) {
      CommitI commitObj = createCommitI(commit);
      result.add(commitObj);
    }
  }

  private CommitI createCommitI(GitCommit commit) {
    CommitI commitObj =
      new Commit(commit.getShortHash().getString(), commit.getDate().getTime(), myUsersIndex.put(commit.getAuthor()));
    commitObj = myRootHolder.decorateByRoot(commitObj);
    return commitObj;
  }

  private void initSymbRefs() {
    if (mySymbolicRefs == null) {
      try {
        mySymbolicRefs = myLowLevelAccess.getRefs();
        myMediator.reportSymbolicRefs(myTicket, myRootHolder.getRoot(), mySymbolicRefs);
      }
      catch (VcsException e) {
        myMediator.acceptException(e);
      }
    }
  }

  public SymbolicRefs getSymbolicRefs() {
    return mySymbolicRefs;
  }
}
