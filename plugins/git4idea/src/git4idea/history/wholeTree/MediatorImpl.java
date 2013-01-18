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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.CalledInBackground;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.Ticket;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import git4idea.history.browser.CachedRefs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author irengrig
 */
public class MediatorImpl implements Mediator {
  private boolean myHaveRestrictingFilters;
  private final Ticket myTicket;
  private final Project myProject;
  private final GitCommitsSequentially myGitCommitsSequentially;
  private TableWrapper myTableWrapper;
  private UIRefresh myUIRefresh;
  private Loader myLoader;
  private final LoadGrowthController myController;
  private Map<VirtualFile, SequenceSupportBuffer> mySequenceBuffers;
  private DetailsLoader myDetailsLoader;

  public MediatorImpl(final Project project, GitCommitsSequentially gitCommitsSequentially) {
    myProject = project;
    myGitCommitsSequentially = gitCommitsSequentially;
    myTicket = new Ticket();
    myController = new LoadGrowthController();
    mySequenceBuffers = new HashMap<VirtualFile, SequenceSupportBuffer>();
    myHaveRestrictingFilters = false;
  }

  @CalledInBackground
  @Override
  public StepType appendResult(final Ticket ticket,
                               final List<CommitI> result,
                               final @Nullable List<List<AbstractHash>> parents,
                               VirtualFile root, boolean checkForSequential) {
    if (! myTicket.equals(ticket)) {
      return StepType.STOP;
    }

    if (! result.isEmpty()) {
      /*if (mySequenceBuffers != null && checkForSequential) {
        try {
          mySequenceBuffers.get(root).appendResult(ticket, result, parents);
        }
        catch (VcsException e) {
          // todo
          myUIRefresh.acceptException(e);
          myTableWrapper.forceStop();
          return StepType.STOP;
        }
      } else {*/
        myTableWrapper.appendResult(ticket, result, parents);
      //}
    }
    if (myTableWrapper.isSuspend()) {
      return StepType.PAUSE;
    }
    return StepType.CONTINUE;
  }

  @CalledInBackground
  @Override
  public void reportSymbolicRefs(final Ticket ticket, final VirtualFile root, final CachedRefs symbolicRefs) {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (!myTicket.equals(ticket)) return;
        myDetailsLoader.reportRefs(root, symbolicRefs);
        myUIRefresh.reportSymbolicRefs(root, symbolicRefs);
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    } else {
      SwingUtilities.invokeLater(runnable);
    }
  }

  @CalledInAwt
  @Override
  public void continueLoading() {
    myTableWrapper.clearSuspend();
    myLoader.resume();
  }

  @Override
  public void forceStop() {
    myTableWrapper.forceStop();
  }

  @Override
  public void acceptException(VcsException e) {
    myUIRefresh.acceptException(e);
  }

  @Override
  public void acceptStashHead(Ticket ticket, VirtualFile root, Pair<AbstractHash, AbstractHash> hash) {
    if (! myTicket.equals(ticket)) return;
    myUIRefresh.reportStash(root, hash);
  }

  @Override
  public void oneFinished() {
    if (myController.isEmpty()) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myUIRefresh.finished();
          myTableWrapper.finished();
        }
      });
    }
  }

  @CalledInAwt
  @Override
  public void reloadSetFixed(final Map<AbstractHash, Long> starred, final RootsHolder rootsHolder) {
    myTicket.increment();

    final List<AbstractHash> hash = new ArrayList<AbstractHash>(starred.keySet());
    Collections.sort(hash, new Comparator<AbstractHash>() {
      @Override
      public int compare(AbstractHash o1, AbstractHash o2) {
        return Comparing.compare(starred.get(o2), starred.get(o1));
      }
    });

    final boolean multipleRoots = rootsHolder.multipleRoots();
    final List<CommitI> filtered = new ArrayList<CommitI>();
    final List<AbstractHash> missing = new ArrayList<AbstractHash>();
    int cInHashes = 0;
    final int count = myTableWrapper.myTableModel.getRowCount();
    for (int i = 0; i < count && hash.size() > cInHashes; i++) {
      final CommitI at = myTableWrapper.myTableModel.getCommitAt(i);
      if (at.holdsDecoration()) continue;
      final AbstractHash obj = hash.get(cInHashes);
      if (at.getHash().equals(obj)) {
        // commit + multiple repo
        final Commit commit = new Commit(at.getHash().getString(), at.getTime(), at.getAuthorIdx());
        if (multipleRoots) {
          filtered.add(new MultipleRepositoryCommitDecorator(commit, at.selectRepository(SelectorList.getInstance())));
        } else {
          filtered.add(commit);
        }
        ++ cInHashes;
      } else if (starred.get(obj) > at.getTime()) {
        missing.add(obj);
        ++ cInHashes;
      }
    }

    for (; cInHashes < hash.size(); ++ cInHashes) {
      missing.add(hash.get(cInHashes));
    }

    myTableWrapper.reset(false, true);
    myController.reset();
    myHaveRestrictingFilters = true;

    if (! filtered.isEmpty()) {
      myTableWrapper.appendResult(myTicket.copy(), filtered, Collections.<List<AbstractHash>>emptyList());
    }
    if (! missing.isEmpty()) {
      final GitLogFilters filters =
        new GitLogFilters(null, null, null, null, ObjectsConvertor.convert(missing, new Convertor<AbstractHash, String>() {
          @Override
          public String convert(AbstractHash o) {
            return o.getString();
          }
        }));
      filters.setUseOnlyHashes(true);
      myLoader.loadSkeleton(myTicket.copy(), rootsHolder, Collections.<String>emptyList(), filters, myController, true);
    } else {
      myUIRefresh.finished();
      myTableWrapper.finished();
    }
  }

  @CalledInAwt
  @Override
  public void reload(final RootsHolder rootsHolder,
                     final Collection<String> startingPoints,
                     Collection<String> endPoints, final GitLogFilters filters, final boolean topoOrder) {
    myTicket.increment();
    myTableWrapper.reset(! filters.haveDisordering(), startingPoints.isEmpty());
    myController.reset();

    myHaveRestrictingFilters = filters.haveCommitterOrCommentFilters();
    /*if (filters.isEmpty()) {
      mySequenceBuffers.clear();
      for (VirtualFile root : rootsHolder.getRoots()) {
        mySequenceBuffers.put(root, new SequenceSupportBuffer(myTableWrapper, myGitCommitsSequentially, root));
      }
    } else {
      mySequenceBuffers = null;
    }*/
    myLoader.loadSkeleton(myTicket.copy(), rootsHolder, startingPoints, filters, myController, topoOrder);
  }

  public void setLoader(Loader loader) {
    myLoader = loader;
  }

  public void setTableModel(BigTableTableModel tableWrapper) {
    myTableWrapper = new TableWrapper(tableWrapper);
  }

  public void setUIRefresh(UIRefresh UIRefresh) {
    myUIRefresh = UIRefresh;
  }

  public void setDetailsLoader(DetailsLoader loader) {
    myDetailsLoader = loader;
  }

  private static boolean pageSizeOk(final Integer size) {
    return size != null && size > 0;
  }

  @NonNls public static final String GIT_LOG_PAGE_SIZE = "git.log.page.size";
  public static final int ourDefaultPageSize = 1000;
  public final static int ourManyLoadedStep = (! pageSizeOk(Integer.getInteger(GIT_LOG_PAGE_SIZE))) ? ourDefaultPageSize : Integer.getInteger(
    GIT_LOG_PAGE_SIZE);

  private static class SequenceSupportBuffer {
    private CommitI myTail;
    private List<CommitI> myCommits;
    private List<List<AbstractHash>> myParents;
    private final TableWrapper myTableWrapper;
    private final GitCommitsSequentially myCommitsSequentially;
    private final VirtualFile myRoot;

    private SequenceSupportBuffer(TableWrapper tableWrapper, GitCommitsSequentially commitsSequentially, final VirtualFile root) {
      myTableWrapper = tableWrapper;
      myCommitsSequentially = commitsSequentially;
      myRoot = root;
      myCommits = Collections.emptyList();
      myParents = Collections.emptyList();
    }

    public void appendResult(final Ticket ticket, final List<CommitI> result,
                                 final @Nullable List<List<AbstractHash>> parents) throws VcsException {
      assert result.size() == parents.size();
      result.addAll(myCommits);
      parents.addAll(myParents);

      final SortListsByFirst sortListsByFirst = new SortListsByFirst(result, CommitIComparator.getInstance());
      sortListsByFirst.sortAnyOther(parents);

      final long commitTime = myTail == null ? result.get(0).getTime() : myTail.getTime();
      final Ref<Integer> cnt = new Ref<Integer>(myTail == null ? 0 : -1);
      myCommitsSequentially.iterateDescending(myRoot, commitTime, new Processor<Pair<AbstractHash, Long>>() {
        boolean startFound = false;

        @Override
        public boolean process(Pair<AbstractHash, Long> pair) {
          if (cnt.get() == -1) {
            if (! pair.getFirst().getString().startsWith(myTail.getHash().getString())) {
              System.out.println("!!!!!!!!! (1) pair: " + pair.getFirst().getString() + " commit (" + cnt.get() + "): " + myTail.getHash().getString());
              return (! startFound) ? pair.getSecond() == commitTime : false;
            } else {
              cnt.set(0);
              startFound = true;
              return true;
            }
          }
          if (! pair.getFirst().getString().startsWith(result.get(cnt.get()).getHash().getString())) {
            System.out.println("(2) pair: " + pair.getFirst().getString() + " commit (" + cnt.get() + "): " + result.get(cnt.get()).getHash().getString());
            return (! startFound) ? pair.getSecond() == commitTime : false;
          }
          cnt.set(cnt.get() + 1);
          startFound = true;
          return cnt.get() < result.size();
        }
      });

      myCommits = new ArrayList<CommitI>(result.subList(cnt.get(), result.size()));
      myParents = new ArrayList<List<AbstractHash>>(parents.subList(cnt.get(), parents.size()));

      if (cnt.get() > 0) {
        myTableWrapper.appendResult(ticket, result.subList(0, cnt.get()), parents.subList(0, cnt.get()));
        myTail = result.get(cnt.get() - 1);
      }
    }
  }

  // in order to don't forget resets etc
  // in fact, data is linked to table state...
  private class TableWrapper {
    private int myRecentCut;
    // queried from background
    private volatile boolean mySuspend;
    private boolean myForcedStop;
    private final BigTableTableModel myTableModel;

    public TableWrapper(final BigTableTableModel tableModel) {
      myTableModel = tableModel;
      myRecentCut = 0;
      mySuspend = false;
      myForcedStop = false;
    }

    @CalledInBackground
    public boolean isSuspend() {
      return mySuspend;
    }

    @CalledInBackground
    public void appendResult(final Ticket ticket, final List<CommitI> result,
                             final @Nullable List<List<AbstractHash>> parents) {
      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          if (! myTicket.equals(ticket)) return;
          // todo check for continuation right here
          myTableModel.appendData(result, parents);
          if (myController.isEmpty()) {
            myTableModel.restore();
            mySuspend = false;
          } else if (! myForcedStop) {
            int nextCut = nextCut();
            if ((nextCut + 1) < myTableModel.getTrueCount()) {
              if (nextCut > myRecentCut) {
                mySuspend = true;
                myRecentCut = nextCut;
                myTableModel.cutAt(myRecentCut);
              }
            }
          }
          myUIRefresh.linesReloaded(mySuspend);
          if (myController.isEmpty()) {
            myUIRefresh.finished();
          }
        }
      };
      if (ApplicationManager.getApplication().isDispatchThread()) {
        runnable.run();
      } else {
        SwingUtilities.invokeLater(runnable);
      }
    }

    private int nextCut() {
      int nextCut = myRecentCut;
      while (true) {
        final CommitI commitAt = myTableModel.getCommitAt(nextCut + getLoadSize());
        if (commitAt != null && myController.isEverybodyLoadedMoreThan(commitAt.getTime())) {
          nextCut += getLoadSize();
          continue;
        }
        break;
      }
      return nextCut;
    }

    public void finished() {
      mySuspend = false;
      myForcedStop = false;
      myTableModel.restore();
    }

    @CalledInAwt
    public void clearSuspend() {
      mySuspend = false;
      myForcedStop = false;
//      myTableModel.restore();
    }

    @CalledInAwt
    public void reset(boolean noFilters, boolean noStartingPoints) {
      mySuspend = false;
      myForcedStop = false;
      myRecentCut = 0;
      myTableModel.clear(noFilters, noStartingPoints);
    }

    @CalledInAwt
    public void forceStop() {
      mySuspend = true;
      myForcedStop = true;
    }
  }

  private int getLoadSize() {
    return myHaveRestrictingFilters ? ourManyLoadedStep/2 : ourManyLoadedStep;
  }
}
