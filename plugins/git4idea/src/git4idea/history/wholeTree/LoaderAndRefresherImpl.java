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

import com.intellij.openapi.application.ApplicationManager;
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
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.*;

import java.util.*;

/**
 * @author irengrig
 */
public class LoaderAndRefresherImpl implements LoaderAndRefresher {
  private final static int ourTestCount = 5;
  private final static int ourSlowPreloadCount = 50;

  private final Collection<String> myStartingPoints;
  private final Mediator.Ticket myTicket;
  private final Collection<ChangesFilter.Filter> myFilters;
  private final Mediator myMediator;
  private final DetailsCache myDetailsCache;
  private final Project myProject;
  private volatile boolean myInterrupted;
  private final Getter<Boolean> myProgressAnalog;
  private BufferedListConsumer<CommitHashPlusParents> myBufferConsumer;
  private Consumer<List<CommitHashPlusParents>> myRealConsumer;
  private final MyRootHolder myRootHolder;
  private final UsersIndex myUsersIndex;

  private final boolean myLoadParents;
  private RepeatingLoadConsumer<CommitHashPlusParents> myRepeatingLoadConsumer;
  private LowLevelAccessImpl myLowLevelAccess;
  private SymbolicRefs mySymbolicRefs;

  public LoaderAndRefresherImpl(final Mediator.Ticket ticket,
                                Collection<ChangesFilter.Filter> filters,
                                Mediator mediator,
                                Collection<String> startingPoints,
                                DetailsCache detailsCache, Project project, MyRootHolder rootHolder, final UsersIndex usersIndex) {
    myRootHolder = rootHolder;
    myUsersIndex = usersIndex;
    myLoadParents = filters == null || filters.isEmpty();
    myTicket = ticket;
    myFilters = filters;
    myMediator = mediator;
    myStartingPoints = startingPoints;
    myDetailsCache = detailsCache;
    myProject = project;
    myInterrupted = false;
    myProgressAnalog = new Getter<Boolean>() {
      @Override
      public Boolean get() {
        return myInterrupted;
      }
    };
    myLowLevelAccess = new LowLevelAccessImpl(myProject, myRootHolder.getRoot());

    myRealConsumer = new Consumer<List<CommitHashPlusParents>>() {
      @Override
      public void consume(final List<CommitHashPlusParents> list) {
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

        if(! myMediator.appendResult(myTicket, buffer, parents)) {
          myInterrupted = true;
        }
      }
    };
    myBufferConsumer = new BufferedListConsumer<CommitHashPlusParents>(15, myRealConsumer, 400);
    myRepeatingLoadConsumer = new RepeatingLoadConsumer<CommitHashPlusParents>(myProject, myBufferConsumer.asConsumer());
  }

  public void interrupt() {
    myInterrupted = true;
  }

  @Override
  public boolean flushIntoUI() {
    myBufferConsumer.flush();
    return ! myInterrupted;
  }

  @Override
  public LoadAlgorithm.Result load(final LoadAlgorithm.LoadType loadType) {
    if (myInterrupted) return new LoadAlgorithm.Result(true, 0);
    initSymbRefs();
    myRepeatingLoadConsumer.reset();
    if (! myStartingPoints.isEmpty()) {
      boolean foundSomething = false;
      for (String point : myStartingPoints) {
        if (point.startsWith(GitBranch.REFS_REMOTES_PREFIX)) {
          if (mySymbolicRefs.getRemoteBranches().contains(point.substring(GitBranch.REFS_REMOTES_PREFIX.length()))) {
            foundSomething = true;
            break;
          }
        } else {
          point = point.startsWith(GitBranch.REFS_HEADS_PREFIX) ? point.substring(GitBranch.REFS_HEADS_PREFIX.length()) : point;
          if (mySymbolicRefs.getLocalBranches().contains(point) || mySymbolicRefs.getTags().contains(point)) {
            foundSomething = true;
            break;
          }
        }
      }
      if (! foundSomething) return new LoadAlgorithm.Result(true, 0);
    }

    final long start = System.currentTimeMillis();
    if (LoadAlgorithm.LoadType.TEST.equals(loadType)) {
      loadFull(ourTestCount);
    } else if (LoadAlgorithm.LoadType.SHORT.equals(loadType)) {
      loadShort();
    } else if (LoadAlgorithm.LoadType.FULL_PREVIEW.equals(loadType)) {
      loadFull(ourSlowPreloadCount);
    } else {
      loadFull(-1);
    }
    final long end = System.currentTimeMillis();
    //todo check
    final List<AbstractHash> lastParents = myRepeatingLoadConsumer.myLastT == null ? null : myRepeatingLoadConsumer.myLastT.getParents();
    return new LoadAlgorithm.Result(lastParents == null || lastParents.isEmpty(), end - start);
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
  private void loadFull(final int count) {
    try {
      myLowLevelAccess.loadCommits(myStartingPoints, Collections.<String>emptyList(), myFilters, new AsynchConsumer<GitCommit>() {
        @Override
        public void consume(GitCommit gitCommit) {
          myDetailsCache.acceptAnswer(Collections.singleton(gitCommit), myRootHolder.getRoot());
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
      myMediator.appendResult(myTicket, result, parents);
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

  private void loadShort() {
    try {
      myLowLevelAccess.loadHashesWithParents(myStartingPoints, myFilters, myRepeatingLoadConsumer, myProgressAnalog);
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
    private int myAlreadyLoaded;
    private int myCnt;
    private T myLastT;

    private RepeatingLoadConsumer(final Project project, Consumer<T> consumer) {
      myProject = project;
      myConsumer = consumer;
      myAlreadyLoaded = 0;
      myCnt = 0;
      myLastT = null;
    }

    public void reset() {
      if (myCnt > myAlreadyLoaded) {
        myAlreadyLoaded = myCnt;
      }
      myCnt = 0;
    }

    @Override
    public void consume(T t) {
      if (! myProject.isOpen()) throw new ProcessCanceledException();
      ++ myCnt;
      myLastT = t;
      if (myCnt > myAlreadyLoaded) {
        myConsumer.consume(t);
      }
    }

    @Override
    public void finished() {
    }
  }
}
