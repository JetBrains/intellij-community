/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.BufferedListConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import git4idea.GitBranch;
import git4idea.changes.GitChangeUtils;
import git4idea.history.browser.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author irengrig
 */
public class LoaderAndRefresherImpl implements LoaderAndRefresher<CommitHashPlusParents> {
  private final static int ourFirstLoadCount = 15;
  private final static int ourPreload = (! parameterCheck(Integer.getInteger("git.log.preload.size"))) ? 100 : Integer.getInteger("git.log.preload.size");

  private final Collection<String> myStartingPoints;
  private final Mediator.Ticket myTicket;
  private final Collection<ChangesFilter.Filter> myFilters;
  private final Mediator myMediator;
  private final DetailsCache myDetailsCache;
  private final Project myProject;
  private final Getter<Boolean> myProgressAnalog;
  private BufferedListConsumer<CommitHashPlusParents> myBufferConsumer;
  private Consumer<List<CommitHashPlusParents>> myRealConsumer;
  private final MyRootHolder myRootHolder;
  private final UsersIndex myUsersIndex;

  private final boolean myLoadParents;
  private RepeatingLoadConsumer<CommitHashPlusParents> myRepeatingLoadConsumer;
  private LowLevelAccessImpl myLowLevelAccess;
  private SymbolicRefs mySymbolicRefs;
  private final LoadGrowthController.ID myId;
  // state
  @NotNull
  private volatile StepType myStepType;

  private static boolean parameterCheck(final Integer i) {
    return i != null && i > 0;
  }

  public LoaderAndRefresherImpl(final Mediator.Ticket ticket,
                                Collection<ChangesFilter.Filter> filters,
                                Mediator mediator,
                                Collection<String> startingPoints,
                                DetailsCache detailsCache,
                                Project project,
                                MyRootHolder rootHolder,
                                final UsersIndex usersIndex,
                                final LoadGrowthController.ID id) {
    myRootHolder = rootHolder;
    myUsersIndex = usersIndex;
    myId = id;
    myLoadParents = filters == null || filters.isEmpty();
    myTicket = ticket;
    myFilters = filters;
    myMediator = mediator;
    myStartingPoints = startingPoints;
    myDetailsCache = detailsCache;
    myStepType = StepType.CONTINUE;
    myProject = project;
    myProgressAnalog = new Getter<Boolean>() {
      @Override
      public Boolean get() {
        return isInterrupted();
      }
    };
    myLowLevelAccess = new LowLevelAccessImpl(myProject, myRootHolder.getRoot());

    myRealConsumer = new Consumer<List<CommitHashPlusParents>>() {
      @Override
      public void consume(final List<CommitHashPlusParents> list) {
        if (isInterrupted()) return;
        final List<CommitI> buffer = new ArrayList<CommitI>();
        final List<List<AbstractHash>> parents = myLoadParents ? new ArrayList<List<AbstractHash>>() : null;
        for (CommitHashPlusParents commitHashPlusParents : list) {
          CommitI commit = new Commit(commitHashPlusParents.getHash(), commitHashPlusParents.getTime(),
                                      myUsersIndex.put(commitHashPlusParents.getAuthorName()));
          commit = myRootHolder.decorateByRoot(commit);
          buffer.add(commit);
          if (myLoadParents) {
            parents.add(commitHashPlusParents.getParents());
          }
        }

        StepType stepType = myMediator.appendResult(myTicket, buffer, parents);
        if (! StepType.FINISHED.equals(myStepType)) {
          myStepType = stepType;
        }
      }
    };
    myBufferConsumer = new BufferedListConsumer<CommitHashPlusParents>(15, myRealConsumer, 400);
    myRepeatingLoadConsumer = new RepeatingLoadConsumer<CommitHashPlusParents>(myProject, myBufferConsumer.asConsumer());
  }

  public void interrupt() {
    myStepType = StepType.STOP;
  }

  public boolean isInterrupted() {
    return StepType.STOP.equals(myStepType);
  }

  @Override
  public StepType flushIntoUI() {
    myBufferConsumer.flush();
    if (StepType.FINISHED.equals(myStepType)) {
      myMediator.oneFinished();
    }
    return myStepType;
  }

  @Override
  public LoadAlgorithm.Result<CommitHashPlusParents> load(final LoadAlgorithm.LoadType loadType, long continuation) {
    if (isInterrupted()) return new LoadAlgorithm.Result<CommitHashPlusParents>(true, 0, myRepeatingLoadConsumer.getLast());
    initSymbRefs();
    if (! myStartingPoints.isEmpty()) {
      if (! checkStartingPoints()) return new LoadAlgorithm.Result<CommitHashPlusParents>(true, 0, myRepeatingLoadConsumer.getLast());
    }

    myRepeatingLoadConsumer.reset();
    int count = MediatorImpl.ourManyLoadedStep;
    boolean shouldFull = true;
    if (LoadAlgorithm.LoadType.TEST.equals(loadType)) {
      count = ourFirstLoadCount;
    } else if (LoadAlgorithm.LoadType.SHORT.equals(loadType) || LoadAlgorithm.LoadType.SHORT_START.equals(loadType)) {
      shouldFull = false;
    } else if (LoadAlgorithm.LoadType.FULL_PREVIEW.equals(loadType)) {
      count = ourPreload;
    }

    long start;
    boolean isOver = false;
    while (true) {
      start = System.currentTimeMillis();
      step(count, shouldFull, continuation);
      if (isInterrupted()) return new LoadAlgorithm.Result<CommitHashPlusParents>(true, 0, myRepeatingLoadConsumer.getLast());
      final List<AbstractHash> lastParents = myRepeatingLoadConsumer.getLast() == null ? null : myRepeatingLoadConsumer.getLast().getParents();
      // at least 1 record would be found, the latest
      isOver = lastParents == null || lastParents.isEmpty() || (myRepeatingLoadConsumer.getTotalRecordsInPack() < count);

      if (isOver) {
        myId.finished();
        myStepType = StepType.FINISHED;
        break;
      }
      if (myRepeatingLoadConsumer.sincePoint() == 0) {
        count *= 2;
        myRepeatingLoadConsumer.reset();
      } else {
        break;
      }
    }
    final long end = System.currentTimeMillis();
    if (! isOver && (myRepeatingLoadConsumer.getLast() != null)) {
      myId.registerTime(myRepeatingLoadConsumer.getLast().getTime());
    }

    return new LoadAlgorithm.Result<CommitHashPlusParents>(isOver, end - start, myRepeatingLoadConsumer.getLast());
  }

  private void step(final int count, final boolean shouldFull, final long continuation) {
    if (shouldFull) {
      loadFull(count, continuation);
    } else {
      loadShort(continuation, count);
    }
  }

  private boolean checkStartingPoints() {
    for (String point : myStartingPoints) {
      if (point.startsWith(GitBranch.REFS_REMOTES_PREFIX)) {
        if (mySymbolicRefs.getRemoteBranches().contains(point.substring(GitBranch.REFS_REMOTES_PREFIX.length()))) {
          return true;
        }
      } else {
        point = point.startsWith(GitBranch.REFS_HEADS_PREFIX) ? point.substring(GitBranch.REFS_HEADS_PREFIX.length()) : point;
        if (mySymbolicRefs.getLocalBranches().contains(point) || mySymbolicRefs.getTags().contains(point)) {
          return true;
        }
      }
    }
    return false;
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

  // true - load is complete //if (gitCommit.getParentsHashes().isEmpty())
  private void loadFull(final int count, final long continuation) {
    try {
      final Collection<ChangesFilter.Filter> filters = addContinuation(continuation);
      myLowLevelAccess.loadCommits(myStartingPoints, Collections.<String>emptyList(), filters, new AsynchConsumer<GitCommit>() {
        @Override
        public void consume(GitCommit gitCommit) {
          if (gitCommit.getParentsHashes().size() <= 1) {
            myDetailsCache.acceptAnswer(Collections.singleton(gitCommit), myRootHolder.getRoot());
          }
          myRepeatingLoadConsumer.consume(GitCommitToCommitConvertor.getInstance().convert(gitCommit));
        }

        @Override
        public void finished() {
        }
      }, count, myProgressAnalog, mySymbolicRefs);
    }
    catch (VcsException e) {
      myMediator.acceptException(e);
    }
  }

  private Collection<ChangesFilter.Filter> addContinuation(long continuation) {
    Collection<ChangesFilter.Filter> filters;
    if (continuation > 0) {
      filters = new ArrayList<ChangesFilter.Filter>(myFilters);
      filters.add(new ChangesFilter.BeforeDate(new Date(continuation)));
    } else {
      filters = myFilters;
    }
    return filters;
  }

  public void loadByHashesAside(final List<String> hashes) {
    final List<CommitI> result = new ArrayList<CommitI>();
    final List<List<AbstractHash>> parents = myLoadParents ? new ArrayList<List<AbstractHash>>() : null;
    for (String hash : hashes) {
      try {
        final SHAHash shaHash = GitChangeUtils.commitExists(myProject, myRootHolder.getRoot(), hash);
        if (shaHash == null) continue;
        final List<GitCommit> commits = myLowLevelAccess.getCommitDetails(Collections.singletonList(shaHash.getValue()), mySymbolicRefs);
        myDetailsCache.acceptAnswer(commits, myRootHolder.getRoot());
        appendCommits(result, parents, commits);
      }
      catch (VcsException e1) {
        continue;
      }
    }
    if (! result.isEmpty()) {
      final StepType stepType = myMediator.appendResult(myTicket, result, parents);
      // here we react only on "stop", not on "pause"
      if (StepType.STOP.equals(stepType)) {
        myStepType = StepType.STOP;
      }
    }
  }

  private void appendCommits(List<CommitI> result, List<List<AbstractHash>> parents, List<GitCommit> commits) {
    for (GitCommit commit : commits) {
      final Commit commitObj =
        new Commit(commit.getShortHash().getString(), commit.getDate().getTime(), myUsersIndex.put(commit.getAuthor()));
      if (parents != null) {
        final Set<String> parentsHashes = commit.getParentsHashes();
        parents.add(ObjectsConvertor.convert(parentsHashes, new Convertor<String, AbstractHash>() {
          @Override
          public AbstractHash convert(String o) {
            return AbstractHash.create(o);
          }
        }));
      }
      result.add(myRootHolder.decorateByRoot(commitObj));
    }
  }

  private void loadShort(final long continuation, int maxCount) {
    final Collection<ChangesFilter.Filter> filters = addContinuation(continuation);
    try {
      myLowLevelAccess.loadHashesWithParents(myStartingPoints, filters, myRepeatingLoadConsumer, myProgressAnalog, maxCount);
    }
    catch (VcsException e) {
      myMediator.acceptException(e);
    }
  }

  interface MyRootHolder {
    VirtualFile getRoot();
    CommitI decorateByRoot(final CommitI commitI);
  }

  static class OneRootHolder implements MyRootHolder {
    private final VirtualFile myVirtualFile;

    OneRootHolder(VirtualFile virtualFile) {
      myVirtualFile = virtualFile;
    }

    @Override
    public CommitI decorateByRoot(CommitI commitI) {
      return commitI;
    }

    @Override
    public VirtualFile getRoot() {
      return myVirtualFile;
    }
  }

  static class ManyCaseHolder implements MyRootHolder {
    private final RootsHolder myRootsHolder;
    private final int myNum;

    ManyCaseHolder(int num, RootsHolder rootsHolder) {
      myNum = num;
      myRootsHolder = rootsHolder;
    }

    @Override
    public CommitI decorateByRoot(CommitI commitI) {
      return new MultipleRepositoryCommitDecorator(commitI, myNum);
    }

    @Override
    public VirtualFile getRoot() {
      return myRootsHolder.get(myNum);
    }
  }

  private static class RepeatingLoadConsumer<T> implements AsynchConsumer<T> {
    private final Project myProject;
    private final Consumer<T> myConsumer;
    private int myCnt;
    private T myLastT;
    private int mySavedPoint;
    private int myTotalRecordsInPack;
    private boolean myPointMeet;

    private RepeatingLoadConsumer(final Project project, Consumer<T> consumer) {
      myProject = project;
      myConsumer = consumer;
      mySavedPoint = 0;
      myCnt = 0;
      myLastT = null;
      myPointMeet = false;
    }

    public int reset() {
      mySavedPoint = myCnt;
      myTotalRecordsInPack = 0;
      myPointMeet = false;
      return mySavedPoint;
    }

    public int getTotalRecordsInPack() {
      return myTotalRecordsInPack;
    }

    public int sincePoint() {
      return myCnt - mySavedPoint;
    }

    public T getLast() {
      return myLastT;
    }

    @Override
    public void consume(T t) {
      if (! myProject.isOpen()) throw new ProcessCanceledException();
      ++ myTotalRecordsInPack;
      if (myLastT == null) {
        myPointMeet = true;
      }
      if (! myPointMeet) {
        myPointMeet = t.equals(myLastT);
      } else {
        ++ myCnt;
        myConsumer.consume(t);
        myLastT = t;
      }
    }

    @Override
    public void finished() {
    }
  }
}
