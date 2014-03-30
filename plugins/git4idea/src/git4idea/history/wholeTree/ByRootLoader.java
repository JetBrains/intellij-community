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
import com.intellij.util.Ticket;
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
  private CachedRefs mySymbolicRefs;
  private final Ticket myTicket;
  private final UsersIndex myUsersIndex;
  private final Collection<String> myStartingPoints;
  @NotNull
  private final GitLogFilters myGitLogFilters;

  public ByRootLoader(Project project,
                      LoaderAndRefresherImpl.MyRootHolder rootHolder,
                      Mediator mediator,
                      DetailsCache detailsCache,
                      Ticket ticket, UsersIndex usersIndex, GitLogFilters gitLogFilters, final Collection<String> startingPoints) {
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
    /*progress(pi, "Load stashed");
    loadStash();*/
    //reportStashHead();
    loadByHashesAside(context);
  }

  private void reportStashHead() {
    progress("Getting stash head");
    try {
      myMediator.acceptStashHead(myTicket, myRootHolder.getRoot(), GitHistoryUtils.getStashTop(myProject, myRootHolder.getRoot()));
    }
    catch (VcsException e) {
      myMediator.acceptException(e);
    }
  }

  private void progress(final String progress) {
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
    if (pi != null) {
      pi.checkCanceled();
      pi.setText(progress);
    }
  }

  private void loadStash() {
    // start is not on a branch
    if (myStartingPoints != null && (! myStartingPoints.isEmpty())) return;

    final List<GitHeavyCommit> details = new ArrayList<GitHeavyCommit>();
    final List<CommitI> commits = new ArrayList<CommitI>();
    final Map<AbstractHash, String> stashMap = new HashMap<AbstractHash, String>();
    final List<List<AbstractHash>> parents = ! myGitLogFilters.haveDisordering() ? new ArrayList<List<AbstractHash>>() : null;

    myGitLogFilters.callConsumer(new Consumer<List<ChangesFilter.Filter>>() {
      @Override
      public void consume(List<ChangesFilter.Filter> filters) {
        ProgressManager.checkCanceled();
        try {
          final List<String> parameters = new ArrayList<String>();
          final List<VirtualFile> paths = new ArrayList<VirtualFile>();
          ChangesFilter.filtersToParameters(filters, parameters, paths);
          final List<Pair<String,GitHeavyCommit>> stash = GitHistoryUtils.loadStashStackAsCommits(myProject, myRootHolder.getRoot(),
                                                                             mySymbolicRefs, parameters.toArray(new String[parameters.size()]));
          if (stash == null) return;
          for (Pair<String, GitHeavyCommit> pair : stash) {
            ProgressManager.checkCanceled();
            final GitHeavyCommit gitCommit = pair.getSecond();
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
    myMediator.appendResult(myTicket, commits, parents, myRootHolder.getRoot(), false);
  }

  // if there're filters -> parents shouldn't be loaded
  public void loadByHashesAside(final ContinuationContext context) {
    final List<CommitI> result = new ArrayList<CommitI>();
    final Set<SHAHash> controlSet = new HashSet<SHAHash>();

    final List<String> hashes = myGitLogFilters.getPossibleReferencies();
    if (hashes == null) return;
    progress("Try to load by reference");
    myGitLogFilters.callConsumer(new Consumer<List<ChangesFilter.Filter>>() {
      @Override
      public void consume(List<ChangesFilter.Filter> filters) {
        for (String hash : hashes) {
          try {
            final SHAHash shaHash = GitChangeUtils.commitExists(myProject, myRootHolder.getRoot(), hash, Collections.<VirtualFile>emptyList());
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
            final List<GitHeavyCommit> commits = myLowLevelAccess.getCommitDetails(Collections.singletonList(shaHash.getValue()), mySymbolicRefs);
            if (commits.isEmpty()) continue;
            assert commits.size() == 1;
            final GitHeavyCommit commitDetails = commits.get(0);
            boolean isOk = true;
            for (ChangesFilter.Filter filter : filters) {
              isOk = filter.getMemoryFilter().applyInMemory(commitDetails);
              if (! isOk) break;
            }
            if (! isOk) continue;

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
      final StepType stepType = myMediator.appendResult(myTicket, result, null, myRootHolder.getRoot(), true);
      // here we react only on "stop", not on "pause"
      if (StepType.STOP.equals(stepType)) {
        context.cancelEverything();
      }
    }
  }

  private void appendCommits(List<CommitI> result, List<GitHeavyCommit> commits) {
    for (GitHeavyCommit commit : commits) {
      CommitI commitObj = createCommitI(commit);
      result.add(commitObj);
    }
  }

  private CommitI createCommitI(GitHeavyCommit commit) {
    CommitI commitObj =
      new Commit(commit.getShortHash().getString(), commit.getDate().getTime(), myUsersIndex.put(commit.getAuthor()));
    commitObj = myRootHolder.decorateByRoot(commitObj);
    return commitObj;
  }

  public CachedRefs initSymbRefs() {
    if (mySymbolicRefs == null) {
      try {
        mySymbolicRefs = myLowLevelAccess.getRefs();
        myMediator.reportSymbolicRefs(myTicket, myRootHolder.getRoot(), mySymbolicRefs);
      }
      catch (VcsException e) {
        myMediator.acceptException(e);
      }
    }
    return mySymbolicRefs;
  }

  public SymbolicRefsI getSymbolicRefs() {
    return mySymbolicRefs;
  }

  public LoaderAndRefresherImpl.MyRootHolder getRootHolder() {
    return myRootHolder;
  }
}
