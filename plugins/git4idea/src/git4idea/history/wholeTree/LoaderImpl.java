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
package git4idea.history.wholeTree;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.CompoundNumber;
import com.intellij.openapi.vcs.StaticReadonlyList;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BufferedListConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import git4idea.history.browser.ChangesFilter;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.LowLevelAccess;
import git4idea.history.browser.LowLevelAccessImpl;

import java.util.*;

/**
 * @author irengrig
 */
public class LoaderImpl implements Loader {
  private static final long ourTestTimeThreshold = 500;
  private final static int ourBigArraysSize = 10;
  private final static int ourTestCount = 5;
  // todo Object +-
  private final TreeComposite<VisibleLine> myTreeComposite;
  private final Map<VirtualFile, LowLevelAccess> myAccesses;
  private final LinesProxy myLinesCache;
  
  private int myLoadId;
  private boolean mySomeDataShown;
  private final Object myLock;
  
  private GitLogLongPanel.UIRefresh myUIRefresh;
  private final Project myProject;
  private ModalityState myModalityState;

  public LoaderImpl(final Project project,
                    final Collection<VirtualFile> allGitRoots) {
    myProject = project;
    myTreeComposite = new TreeComposite<VisibleLine>(ourBigArraysSize, WithoutDecorationComparator.getInstance());
    myLinesCache = new LinesProxy(myTreeComposite);
    myAccesses = new HashMap<VirtualFile, LowLevelAccess>();
    for (VirtualFile gitRoot : allGitRoots) {
      myAccesses.put(gitRoot, new LowLevelAccessImpl(project, gitRoot));
    }
    myLock = new Object();
    myLoadId = 0;
  }

  public LinesProxy getLinesProxy() {
    return myLinesCache;
  }

  public void setModalityState(ModalityState modalityState) {
    myModalityState = modalityState;
  }

  public void setUIRefresh(GitLogLongPanel.UIRefresh UIRefresh) {
    myUIRefresh = UIRefresh;
  }

  private class MyJoin implements Runnable {
    private final int myId;

    public MyJoin(final int id) {
      myId = id;
    }

    @Override
    public void run() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          synchronized (myLock) {
            if (myId == myLoadId) {
              myUIRefresh.skeletonLoadComplete();
            }
          }
        }
      }, myModalityState, new Condition() {
        @Override
        public boolean value(Object o) {
          return (! (! myProject.isDisposed()) && (myId == myLoadId));
        }
      });
    }
  }

  public TreeComposite<VisibleLine> getTreeComposite() {
    return myTreeComposite;
  }

  @CalledInAwt
  @Override
  public void loadSkeleton(final Collection<String> startingPoints, final Collection<ChangesFilter.Filter> filters) {
    // load first portion, limited, measure time, decide whether to load only ids or load commits...
    final Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();

    final int current;
    synchronized (myLock) {
      current = ++ myLoadId;
      mySomeDataShown = false;
    }
    final boolean drawHierarchy = filters.isEmpty();

    application.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          final Join join = new Join(myAccesses.size(), new MyJoin(current));
          final Runnable joinCaller = new Runnable() {
            @Override
            public void run() {
              join.complete();
            }
          };

          myTreeComposite.clearMembers();

          final List<VirtualFile> list = new ArrayList<VirtualFile>(myAccesses.keySet());
          Collections.sort(list, FilePathComparator.getInstance());

          for (VirtualFile vf : list) {
            final LowLevelAccess access = myAccesses.get(vf);
            final Consumer<CommitHashPlusParents> consumer = createCommitsHolderConsumer(drawHierarchy);
            final Consumer<List<CommitHashPlusParents>> listConsumer = new RefreshingCommitsPackConsumer(current, consumer);

            final BufferedListConsumer<CommitHashPlusParents> bufferedListConsumer =
              new BufferedListConsumer<CommitHashPlusParents>(15, listConsumer, 1000);
            bufferedListConsumer.setFlushListener(joinCaller);

            final long start = System.currentTimeMillis();
            final boolean allDataAlreadyLoaded =
              FullDataLoader.load(myLinesCache, access, startingPoints, filters, bufferedListConsumer.asConsumer(), ourTestCount);
            final long end = System.currentTimeMillis();

            if (allDataAlreadyLoaded) {
              bufferedListConsumer.flush();
            } else {
              final boolean loadFull = (end - start) > ourTestTimeThreshold;
              final LoaderBase loaderBase = new LoaderBase(access, bufferedListConsumer, filters, ourTestCount, loadFull, startingPoints, myLinesCache);
              loaderBase.execute();
            }
          }
        } catch (VcsException e) {
          myUIRefresh.acceptException(e);
        } finally {
          //myUIRefresh.skeletonLoadComplete();
        }
      }
    });
  }

  private Consumer<CommitHashPlusParents> createCommitsHolderConsumer(boolean drawHierarchy) {
    Consumer<CommitHashPlusParents> consumer;
    if (drawHierarchy) {
      final SkeletonBuilder skeletonBuilder = new SkeletonBuilder(ourBigArraysSize, ourBigArraysSize - 1);
      consumer = skeletonBuilder;
      myTreeComposite.addMember(skeletonBuilder.getResult());
    } else {
      final StaticReadonlyList<VisibleLine> readonlyList = new StaticReadonlyList<VisibleLine>(ourBigArraysSize);
      consumer = new Consumer<CommitHashPlusParents>() {
        @Override
        public void consume(CommitHashPlusParents commitHashPlusParents) {
          readonlyList.consume(new TreeSkeletonImpl.Commit(commitHashPlusParents.getHash().getBytes(), 0, commitHashPlusParents.getTime()));
        }
      };
      myTreeComposite.addMember(readonlyList);
    }
    return consumer;
  }

  private static class MyStopListenToOutputException extends RuntimeException {}

  private class RefreshingCommitsPackConsumer implements Consumer<List<CommitHashPlusParents>> {
    private final int myId;
    private final Consumer<CommitHashPlusParents> myConsumer;
    private Application myApplication;
    private final Runnable myRefreshRunnable;
    private final Condition myRefreshCondition;

    public RefreshingCommitsPackConsumer(int id, Consumer<CommitHashPlusParents> consumer) {
      myId = id;
      myConsumer = consumer;
      myApplication = ApplicationManager.getApplication();
      myRefreshRunnable = new Runnable() {
        @Override
        public void run() {
          myTreeComposite.repack();
          if (! mySomeDataShown) {
            // todo remove
            myUIRefresh.setSomeDataReadyState();
          }
          myUIRefresh.fireDataReady(0, myTreeComposite.getSize());
          mySomeDataShown = true;
        }
      };
      myRefreshCondition = new Condition() {
        @Override
        public boolean value(Object o) {
          return (! (! myProject.isDisposed()) && myId == myLoadId);
        }
      };
    }

    @Override
    public void consume(List<CommitHashPlusParents> commitHashPlusParentses) {
      synchronized (myLock) {
        if (myId != myLoadId) throw new MyStopListenToOutputException();
      }
      for (CommitHashPlusParents item : commitHashPlusParentses) {
        myConsumer.consume(item);
      }
      synchronized (myLock) {
        if (myId != myLoadId) throw new MyStopListenToOutputException();
        myApplication.invokeLater(myRefreshRunnable, myModalityState, myRefreshCondition);
        //myApplication.invokeLater(myRefreshRunnable, myModalityState);
      }
    }
  }

  private static class LoaderBase {
    private final boolean myLoadFullData;
    private final BufferedListConsumer<CommitHashPlusParents> myConsumer;
    private final LowLevelAccess myAccess;
    private final Collection<String> myStartingPoints;
    private final Consumer<GitCommit> myLinesCache;
    private final Collection<ChangesFilter.Filter> myFilters;
    private final int myIgnoreFirst;

    public LoaderBase(LowLevelAccess access,
                       BufferedListConsumer<CommitHashPlusParents> consumer,
                       Collection<ChangesFilter.Filter> filters,
                       int ignoreFirst, boolean loadFullData, Collection<String> startingPoints, final Consumer<GitCommit> linesCache) {
      myAccess = access;
      myConsumer = consumer;
      myFilters = filters;
      myIgnoreFirst = ignoreFirst;
      myLoadFullData = loadFullData;
      myStartingPoints = startingPoints;
      myLinesCache = linesCache;
    }

    public void execute() throws VcsException {
      final MyConsumer consumer = new MyConsumer(myConsumer, myIgnoreFirst);

      if (myLoadFullData) {
        FullDataLoader.load(myLinesCache, myAccess, myStartingPoints, myFilters, myConsumer.asConsumer(), -1);
      } else {
        myAccess.loadHashesWithParents(myStartingPoints, myFilters, consumer);
      }
      myConsumer.flush();
    }

    private static class MyConsumer implements Consumer<CommitHashPlusParents> {
      private final int myIgnoreFirst;
      private final BufferedListConsumer<CommitHashPlusParents> myConsumer;
      private int myCnt;

      private MyConsumer(BufferedListConsumer<CommitHashPlusParents> consumer, int ignoreFirst) {
        myConsumer = consumer;
        myIgnoreFirst = ignoreFirst;
        myCnt = 0;
      }

      @Override
      public void consume(CommitHashPlusParents commitHashPlusParents) {
        if (myCnt >= myIgnoreFirst) {
          myConsumer.consumeOne(commitHashPlusParents);
        }
        ++ myCnt;
      }
    }
  }

  // true if there are no more rows
  private static class FullDataLoader {
    private boolean myLoadIsComplete;
    private int myCnt;

    private FullDataLoader() {
      myLoadIsComplete = false;
      myCnt = 0;
    }

    public static boolean load(final Consumer<GitCommit> linesCache, final LowLevelAccess access, final Collection<String> startingPoints,
                               final Collection<ChangesFilter.Filter> filters, final Consumer<CommitHashPlusParents> consumer,
                               final int maxCnt) throws VcsException {
      return new FullDataLoader().loadFullData(linesCache, access, startingPoints, filters, consumer, maxCnt);
    }

    private boolean loadFullData(final Consumer<GitCommit> linesCache, final LowLevelAccess access, final Collection<String> startingPoints,
                               final Collection<ChangesFilter.Filter> filters, final Consumer<CommitHashPlusParents> consumer,
                               final int maxCnt) throws VcsException {
    access.loadCommits(startingPoints, null, null, filters, new Consumer<GitCommit>() {
      @Override
      public void consume(GitCommit gitCommit) {
        linesCache.consume(gitCommit);
        consumer.consume(GitCommitToCommitConvertor.getInstance().convert(gitCommit));
        if (gitCommit.getParentsHashes().isEmpty()) {
          myLoadIsComplete = true;
        }
        ++ myCnt;
      }
    }, maxCnt, Collections.<String>emptyList());
    return myLoadIsComplete || (maxCnt > 0) && (myCnt < maxCnt);
  }
  }

  private static class GitCommitToCommitConvertor implements Convertor<GitCommit, CommitHashPlusParents> {
    private final static GitCommitToCommitConvertor ourInstance = new GitCommitToCommitConvertor();

    public static GitCommitToCommitConvertor getInstance() {
      return ourInstance;
    }

    @Override
    public CommitHashPlusParents convert(GitCommit o) {
      final Set<String> parentsHashes = o.getParentsHashes();
      return new CommitHashPlusParents(o.getShortHash(), parentsHashes.toArray(new String[parentsHashes.size()]), o.getDate().getTime());
    }
  }

  private static class WithoutDecorationComparator implements Comparator<Pair<CompoundNumber, VisibleLine>> {
    private final static WithoutDecorationComparator ourInstance = new WithoutDecorationComparator();

    public static WithoutDecorationComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(Pair<CompoundNumber, VisibleLine> o1, Pair<CompoundNumber, VisibleLine> o2) {
      if (o1 == null || o2 == null) {
        return o1 == null ? -1 : 1;
      }
      final Object obj1 = o1.getSecond();
      final Object obj2 = o2.getSecond();

      if (obj1 instanceof TreeSkeletonImpl.Commit && obj2 instanceof TreeSkeletonImpl.Commit) {
        final long diff;
        if (o1.getFirst().getMemberNumber() == o2.getFirst().getMemberNumber()) {
          // natural order
          diff = o1.getFirst().getIdx() - o2.getFirst().getIdx();
        } else {
          // lets take time here
          diff = - (((TreeSkeletonImpl.Commit)obj1).getTime() - ((TreeSkeletonImpl.Commit)obj2).getTime());
        }
        return diff == 0 ? 0 : (diff < 0 ? -1 : 1);
      }
      return Comparing.compare(obj1.toString(), obj2.toString());
    }
  }
}
