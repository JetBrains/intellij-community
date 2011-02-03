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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.CalledInBackground;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.AbstractCalledLater;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.history.browser.SymbolicRefs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author irengrig
 */
public class MediatorImpl implements Mediator {
  private final Ticket myTicket;
  private final Project myProject;
  private TableWrapper myTableWrapper;
  private UIRefresh myUIRefresh;
  private Loader myLoader;
  private final ModalityState myState;
  private final LoadGrowthController myController;

  public MediatorImpl(final Project project, final ModalityState state) {
    myProject = project;
    myState = state;
    myTicket = new Ticket();
    myController = new LoadGrowthController();
  }

  @CalledInBackground
  @Override
  public StepType appendResult(final Ticket ticket, final List<CommitI> result, final @Nullable List<List<AbstractHash>> parents) {
    if (! myTicket.equals(ticket)) {
      return StepType.STOP;
    }

    myTableWrapper.appendResult(ticket, result, parents);
    if (myTableWrapper.isSuspend()) {
      return StepType.PAUSE;
    }
    return StepType.CONTINUE;
  }

  @CalledInBackground
  @Override
  public void reportSymbolicRefs(final Ticket ticket, final VirtualFile root, final SymbolicRefs symbolicRefs) {
    new AbstractCalledLater(myProject, myState) {
      @Override
      public void run() {
        if (! myTicket.equals(ticket)) return;
        myUIRefresh.reportSymbolicRefs(root, symbolicRefs);
      }
    }.callMe();
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
  public void oneFinished() {
    if (myController.isEmpty()) {
      myUIRefresh.finished();
      myTableWrapper.finished();
    }
  }

  @CalledInAwt
  @Override
  public void reload(final RootsHolder rootsHolder,
                     final Collection<String> startingPoints,
                     final GitLogFilters filters) {
    myTicket.increment();
    myTableWrapper.reset();
    myController.reset();
    myLoader.loadSkeleton(myTicket.copy(), rootsHolder, startingPoints, filters, myController);
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

  private static boolean pageSizeOk(final Integer size) {
    return size != null && size > 0;
  }

  @NonNls public static final String GIT_LOG_PAGE_SIZE = "git.log.page.size";
  public final static int ourManyLoadedStep = (! pageSizeOk(Integer.getInteger(GIT_LOG_PAGE_SIZE))) ? 3000 : Integer.getInteger(
    GIT_LOG_PAGE_SIZE);

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
      new AbstractCalledLater(myProject, myState) {
        @Override
        public void run() {
          if (! myTicket.equals(ticket)) return;
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
      }.callMe();
    }

    private int nextCut() {
      int nextCut = myRecentCut;
      while (true) {
        final CommitI commitAt = myTableModel.getCommitAt(nextCut + ourManyLoadedStep);
        if (commitAt != null && myController.isEverybodyLoadedMoreThan(commitAt.getTime())) {
          nextCut += ourManyLoadedStep;
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
    public void reset() {
      mySuspend = false;
      myForcedStop = false;
      myRecentCut = 0;
      myTableModel.clear();
    }

    @CalledInAwt
    public void forceStop() {
      mySuspend = true;
      myForcedStop = true;
    }
  }
}
