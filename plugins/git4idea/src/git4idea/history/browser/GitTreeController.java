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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.CalledInBackground;
import com.intellij.openapi.vcs.RequestsMerger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUCache;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.config.GitConfigUtil;
import git4idea.history.GitUsersComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class GitTreeController implements ManageGitTreeView {
  private final Project myProject;
  private final VirtualFile myRoot;
  private final GitTreeViewI myTreeView;
  private final GitUsersComponent myGitUsersComponent;

  private final LowLevelAccess myAccess;
  private volatile boolean myInitialized;

  // guarded by lock
  private final AtomicReference<List<String>> myTags;
  private final AtomicReference<List<String>> myBranches;

  private final MyFiltersStateHolder myFilterHolder;
  private final MyFiltersStateHolder myHighlightingHolder;

  private final RequestsMerger myFilterRequestsMerger;

  private final MyUpdateStateInterceptor myFiltering;
  private final MyUpdateStateInterceptor myHighlighting;
  private Alarm myAlarm;

  private final SLRUCache<SHAHash, CommittedChangeList> myListsCache = new SLRUCache<SHAHash, CommittedChangeList>(128, 64) {
    @NotNull
    @Override
    public CommittedChangeList createValue(SHAHash key) {
      try {
        return GitChangeUtils.getRevisionChanges(myProject, myRoot, key.getValue(), true);
      }
      catch (VcsException e) {
        return new CommittedChangeListImpl(e.getMessage(), "", "", -1, null, Collections.<Change>emptyList());
      }
    }
  };
  private Runnable myRefresher;
  private String myUsername;
  private Date myTravelDate;

  GitTreeController(final Project project, final VirtualFile root, final GitTreeViewI treeView, GitUsersComponent gitUsersComponent) {
    myProject = project;
    myRoot = root;
    myTreeView = treeView;
    myGitUsersComponent = gitUsersComponent;
    myAccess = new LowLevelAccessImpl(project, root);

    myFilterHolder = new MyFiltersStateHolder();
    myHighlightingHolder = new MyFiltersStateHolder();

    myTags = new AtomicReference<List<String>>(Collections.<String>emptyList());
    myBranches = new AtomicReference<List<String>>(Collections.<String>emptyList());

    myAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, project);
    myRefresher = new Runnable() {
      public void run() {
        try {
          if (myFilterHolder.isDirty()) {
            final CommitsLoader loader = new CommitsLoader();
            loader.loadCommitsUsingMemoryAndNativeFilters(myFilterHolder.getStartingPoints(), myFilterHolder.getCurrentPoint(), null,
                                                 myFilterHolder.getFilters(), PageSizes.LOAD_SIZE);
            final List<GitCommit> commitList = loader.getCommitList();
            myTravelDate = commitList.isEmpty() ? null : commitList.get(commitList.size() - 1).getDate();
            final SHAHash lastHash = commitList.isEmpty() ? null : commitList.get(commitList.size() - 1).getHash();
            myTreeView.refreshView(commitList, new TravelTicket(loader.isStartFound(), myTravelDate, lastHash), null);

            myFilterHolder.setDirty(false);
          }

          // highlighting
          if (myHighlightingHolder.isNothingSelected()) {
            myTreeView.clearHighlighted();
            return;
          }

          myTreeView.acceptHighlighted(loadIdsToHighlight());
        } catch (VcsException e) {
          myTreeView.acceptError(e.getMessage(), e);
        } finally {
          myTreeView.refreshFinished();
          myProject.getMessageBus().syncPublisher(GitProjectLogManager.CHECK_CURRENT_BRANCH).consume(myRoot);
        }
      }
    };
    myFilterRequestsMerger = new RequestsMerger(myRefresher, new Consumer<Runnable>() {
      public void consume(Runnable runnable) {
        myTreeView.refreshStarted();
        myAlarm.addRequest(runnable, 50);
      }
    });

    myFiltering = new MyUpdateStateInterceptor(myFilterRequestsMerger, myFilterHolder);
    myHighlighting = new MyUpdateStateInterceptor(myFilterRequestsMerger, myHighlightingHolder);
    myFilterHolder.setDirty(true);

    ApplicationManager.getApplication().executeOnPooledThread(new DumbAwareRunnable() {
      public void run() {
        initCurrentUser();
      }
    });
  }

  private class CommitsLoader {
    private boolean myStartFound;
    private final List<GitCommit> myCommitList;
    private GitCommit myLastCommit;

    private CommitsLoader() {
      myCommitList = new ArrayList<GitCommit>(PageSizes.LOAD_SIZE);
    }

    // todo refactor more; mechanically
    private void loadCommitsUsingMemoryAndNativeFilters(final Collection<String> startingPoints, final Pair<Date, SHAHash> beforePoint,
                                    final Date afterPoint, final Collection<ChangesFilter.Filter> filters, final int maxCnt) throws VcsException {
      assert maxCnt > 0;
      final List<ChangesFilter.MemoryFilter> memoryFilters = new LinkedList<ChangesFilter.MemoryFilter>();
      final List<ChangesFilter.Filter> commandFilters = new LinkedList<ChangesFilter.Filter>();
      for (ChangesFilter.Filter filter : filters) {
        final ChangesFilter.CommandParametersFilter commandFilter = filter.getCommandParametersFilter();
        if (commandFilter == null) {
          memoryFilters.add(filter.getMemoryFilter());
        } else {
          commandFilters.add(filter);
        }
      }

      int requestMaxCnt = maxCnt;
      Pair<Date, SHAHash> runningBeforePoint = beforePoint;
      while (myCommitList.size() < maxCnt) {
        final Date lastDate = runningBeforePoint == null ? null : runningBeforePoint.getFirst();
        final SHAHash lastHash = runningBeforePoint == null ? null : runningBeforePoint.getSecond();

        final List<GitCommit> newList = loadPiece(startingPoints, afterPoint, commandFilters, requestMaxCnt, lastDate, lastHash);

        for (GitCommit gitCommit : newList) {
          boolean add = true;
          for (ChangesFilter.MemoryFilter memoryFilter : memoryFilters) {
            if (! memoryFilter.applyInMemory(gitCommit)) {
              add = false;
              break;
            }
          }
          if (add) {
            myCommitList.add(gitCommit);
            if (myCommitList.size() >= maxCnt) break;
          }
        }
        if (myLastCommit == null || myLastCommit.getParentsHashes().isEmpty() || myLastCommit.getHash().equals(lastHash)) {
          myStartFound = true;
          break;
        }
        if (Comparing.equal(lastDate, myLastCommit.getDate())) {
          requestMaxCnt = 2 * maxCnt;
        }
        runningBeforePoint = new Pair<Date, SHAHash>(myLastCommit.getDate(), myLastCommit.getHash());
      }
    }

    private List<GitCommit> loadPiece(Collection<String> startingPoints,
                                      Date afterPoint,
                                      List<ChangesFilter.Filter> commandFilters,
                                      int requestMaxCnt, final Date lastDate, final SHAHash lastHash) throws VcsException {
      final List<GitCommit> newList = new ArrayList<GitCommit>(PageSizes.LOAD_SIZE);
      final Ref<Boolean> oldPointPassed = new Ref<Boolean>();

      myAccess.loadCommits(startingPoints, lastDate, afterPoint, commandFilters, new Consumer<GitCommit>() {
        @Override
        public void consume(GitCommit gitCommit) {
          myLastCommit = gitCommit;

          if (! Boolean.TRUE.equals(oldPointPassed.get())) {
            if (lastDate != null && gitCommit.getDate().before(lastDate)) {
              oldPointPassed.set(true);
              // can continue adding
            } else if (gitCommit.getHash().equals(lastHash)) {
              newList.clear();
              oldPointPassed.set(true);
              return; // ignore everything before and current point
            }
          }
          newList.add(gitCommit);
        }
      }, requestMaxCnt, myBranches.get());
      return newList;
    }

    public List<GitCommit> getCommitList() {
      return myCommitList;
    }

    public boolean isStartFound() {
      return myStartFound;
    }
  }

  private void initCurrentUser() {
    final List<Pair<String,String>> value;
    try {
      value = GitConfigUtil.getAllValues(myProject, myRoot, "user.name");
      myUsername = value.size() == 1 ? value.get(0).getSecond() : null;
    }
    catch (VcsException e) {
      //
    }
  }

  private Set<SHAHash> loadIdsToHighlight() throws VcsException {
    final Portion highlighted;
    // we can ignore extra commit loaded for the same date (boundary), it's easier
    // commits refer to highlighted stuff, so everything will be correct
    if (myTravelDate == null) {
      // todo rewrite when dates are introduced
      highlighted = loadPortion(myHighlightingHolder.getStartingPoints(), myFilterHolder.getCurrentPoint().getFirst(),
                                              null, Collections.<ChangesFilter.Filter>emptyList(), -1);
    } else {
      highlighted = loadPortion(myHighlightingHolder.getStartingPoints(), myFilterHolder.getCurrentPoint().getFirst(),
                                              myTravelDate, Collections.<ChangesFilter.Filter>emptyList(), -1);
    }

    final Collection<ChangesFilter.Filter> filters = myHighlightingHolder.getFilters();
    final List<ChangesFilter.MemoryFilter> combined = ChangesFilter.combineFilters(filters);

    final Set<SHAHash> highlightedIds = new HashSet<SHAHash>();
    highlighted.iterateFrom(0, new Processor<GitCommit>() {
      public boolean process(GitCommit gitCommit) {
        for (ChangesFilter.MemoryFilter filter : combined) {
          if (! filter.applyInMemory(gitCommit)) {
            return false;
          }
        }
        highlightedIds.add(gitCommit.getHash());
        return false;
      }
    });

    return highlightedIds;
  }

  // !!!! after point is included! (should be)
  @NotNull
  private Portion loadPortion(final Collection<String> startingPoints, final Date beforePoint, final Date afterPoint,
                              final Collection<ChangesFilter.Filter> filtersIn, int maxCnt) throws VcsException {
    final Portion portion = new Portion(null);
    myAccess.loadCommits(startingPoints, beforePoint, afterPoint, filtersIn, portion, maxCnt, myBranches.get());
    return portion;
  }

  public SHAHash commitExists(String reference) {
    return GitChangeUtils.commitExists(myProject, myRoot, reference);
  }

  private String getStatusMessage() {
    // todo
    return "Showing";
  }

  private void loadTagsNBranches() {
    final List<String> branches = new LinkedList<String>();
    final List<String> tags = new LinkedList<String>();

    try {
      myAccess.loadAllBranches(branches);
      Collections.sort(branches);
      myBranches.set(branches);
      myAccess.loadAllTags(tags);
      Collections.sort(tags);
      myTags.set(tags);
    }
    catch (VcsException e) {
      myTreeView.acceptError(e.getMessage(), e);
    }
  }

  // no filter - or saved filter? -> then pass
  public void init() {
    assert ! myInitialized;

    myAlarm.addRequest(new Runnable() {
      public void run() {
        myFilterRequestsMerger.request();
        loadTagsNBranches();
        myInitialized = true;
        myTreeView.controllerReady();
      }
    }, 100);
  }

  public boolean hasNext(final TravelTicket ticket) {
    return (ticket == null) || (! ticket.isIsBottomReached());
  }

  public boolean hasPrevious(final TravelTicket ticket) {
    return myFilterHolder.getCurrentPoint() != null;
  }

  public void next(final TravelTicket ticket) {
    myFilterHolder.addContinuationPoint(new Pair<Date, SHAHash>(ticket.getLatestDate(), ticket.getLastHash()));
    myFilterRequestsMerger.request();
  }

  public void previous(final TravelTicket ticket) {
    myFilterHolder.popContinuationPoint();
    myFilterRequestsMerger.request();
  }

  public void navigateTo(@NotNull String reference) {
    SHAHash hash = GitChangeUtils.commitExists(myProject, myRoot, reference);
    if (hash == null) {
      hash = GitChangeUtils.commitExistsByComment(myProject, myRoot, reference);
    }
    if (hash == null) {
      ChangesViewBalloonProblemNotifier.showMe(myProject, "Nothing found for: \"" + reference + "\"", MessageType.WARNING);
    } else {
      final SHAHash finalHash = hash;
      myAlarm.addRequest(new Runnable() {
        public void run() {
          // start from beginning
          final List<Pair<Date, SHAHash>> wayList = new LinkedList<Pair<Date, SHAHash>>();

          while (true) {
            final Pair<Date, SHAHash> startFrom = wayList.isEmpty() ? null : wayList.get(wayList.size() - 1);
            final CommitsLoader loader = new CommitsLoader();
            try {
              loader.loadCommitsUsingMemoryAndNativeFilters(myFilterHolder.getStartingPoints(), startFrom, null, myFilterHolder.getFilters(), PageSizes.LOAD_SIZE);
            }
            catch (VcsException e) {
              myTreeView.acceptError(e.getMessage(), e);
              return;
            }
            final List<GitCommit> commits = loader.getCommitList(); // todo! push contents!!!
            for (GitCommit commit : commits) {
              if (finalHash.equals(commit.getHash())) {
                // select this page
                while (myFilterHolder.getCurrentPoint() != null) {
                  myFilterHolder.popContinuationPoint();
                }
                for (Pair<Date, SHAHash> date : wayList) {
                  myFilterHolder.addContinuationPoint(date);
                }

                final GitCommit lastCommit = commits.get(commits.size() - 1);
                myTreeView.refreshView(commits, new TravelTicket(loader.isStartFound(), lastCommit.getDate(), lastCommit.getHash()), finalHash);
                myFilterHolder.setDirty(false);
                myRefresher.run();
                return;
              }
            }
            if (! commits.isEmpty()) {
              final GitCommit commit = commits.get(commits.size() - 1);
              wayList.add(new Pair<Date, SHAHash>(commit.getDate(), commit.getHash()));
            }
            // no object
            if (loader.isStartFound()) return;
          }
        }
      }, 10);
    }
  }

  public void refresh() {
    myFilterHolder.setDirty(true);
    myFilterRequestsMerger.request();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        loadTagsNBranches();
      }
    }, 100);
  }

  // todo loading indicator, load optimization
  public void getDetails(final Collection<SHAHash> hashes) {
    final Application application = ApplicationManager.getApplication();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        final List<CommittedChangeList> loaded = new LinkedList<CommittedChangeList>();
        final Set<Long> requested = new HashSet<Long>(hashes.size());
        for (SHAHash hash : hashes) {
          requested.add(GitChangeUtils.longForSHAHash(hash.getValue()));

          final CommittedChangeList changeList = myListsCache.get(hash);
          if (requested.contains(changeList.getNumber())) {
            loaded.add(changeList);
          }
        }
        if (! loaded.isEmpty()) {
          application.invokeLater(new Runnable() {
            public void run() {
              myTreeView.acceptDetails(loaded);
            }
          });
        }
      }
    }, 30);
  }

  public GitTreeFiltering getFiltering() {
    return myFiltering;
  }

  public GitTreeFiltering getHighlighting() {
    return myHighlighting;
  }

  public List<String> getKnownUsers() {
    final List<String> list = myGitUsersComponent.getUsersList(myRoot);
    if (myUsername != null && list != null) {
      list.remove(myUsername);
      list.add(0, myUsername);
    }
    return list == null ? Collections.<String>emptyList() : list;
  }

  @CalledInBackground
  public void cherryPick(final Collection<SHAHash> hashes) {
    final CherryPicker picker = new CherryPicker(GitVcs.getInstance(myProject), hashes, myListsCache, myAccess);
    picker.execute();
  }

  public List<String> getAllBranchesOrdered() {
    return new ArrayList<String>(myBranches.get());
  }

  public List<String> getAllTagsOrdered() {
    return new ArrayList<String>(myTags.get());
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  private static class MyUpdateStateInterceptor implements GitTreeFiltering {
    private final MyFiltersStateHolder myState;
    private final RequestsMerger myRequestsMerger;

    protected MyUpdateStateInterceptor(RequestsMerger requestsMerger, MyFiltersStateHolder state) {
      myRequestsMerger = requestsMerger;
      myState = state;
    }

    private void requestRefresh() {
      myRequestsMerger.request();
    }

    public void addFilter(ChangesFilter.Filter filter) {
      myState.addFilter(filter);
      requestRefresh();
    }

    public void removeFilter(ChangesFilter.Filter filter) {
      myState.removeFilter(filter);
      requestRefresh();
    }

    public void addStartingPoint(String ref) {
      myState.addStartingPoint(ref);
      requestRefresh();
    }

    public void removeStartingPoint(String ref) {
      myState.removeStartingPoint(ref);
      requestRefresh();
    }

    public void updateExcludePoints(List<String> points) {
      myState.updateExcludePoints(points);
      requestRefresh();
    }

    @Override
    public void markDirty() {
      myState.markDirty();
      requestRefresh();
    }

    public Collection<String> getStartingPoints() {
      return myState.getStartingPoints();
    }

    public List<String> getExcludePoints() {
      return myState.getExcludePoints();
    }

    public Collection<ChangesFilter.Filter> getFilters() {
      return myState.getFilters();
    }
  }

  private static class MyFiltersStateHolder implements GitTreeFiltering {
    private final Object myLock;
    private final Set<String> myStartingPoints;
    private boolean myDirty;

    private final List<Pair<Date, SHAHash>> myContinuationPoints;

    @Nullable
    private List<String> myExcludePoints;
    private final Collection<ChangesFilter.Filter> myFilters;

    private MyFiltersStateHolder() {
      myLock = new Object();
      myStartingPoints = new HashSet<String>();
      myFilters = new HashSet<ChangesFilter.Filter>();
      myContinuationPoints = new LinkedList<Pair<Date, SHAHash>>();
    }

    public boolean isDirty() {
      synchronized (myLock) {
        return myDirty;
      }
    }

    public void setDirty(boolean dirty) {
      synchronized (myLock) {
        myDirty = dirty;
      }
    }

    @Nullable
    public List<String> getExcludePoints() {
      synchronized (myLock) {
        return myExcludePoints;
      }
    }

    // page starts
    public void addContinuationPoint(final Pair<Date, SHAHash> hashPair) {
      synchronized (myLock) {
        if ((! myContinuationPoints.isEmpty()) && myContinuationPoints.get(myContinuationPoints.size() - 1).equals(hashPair)) return;
        myDirty = true;
        myContinuationPoints.add(hashPair);
      }
    }

    @Nullable
    public Pair<Date, SHAHash> popContinuationPoint() {
      synchronized (myLock) {
        myDirty = true;
        return myContinuationPoints.isEmpty() ? null : myContinuationPoints.remove(myContinuationPoints.size() - 1);
      }
    }

    public Pair<Date, SHAHash> getCurrentPoint() {
      synchronized (myLock) {
        return myContinuationPoints.isEmpty() ? null : myContinuationPoints.get(myContinuationPoints.size() - 1);
      }
    }

    public void addFilter(ChangesFilter.Filter filter) {
      synchronized (myLock) {
        myDirty = true;
        myFilters.add(filter);
        myContinuationPoints.clear();
      }
    }

    public void removeFilter(ChangesFilter.Filter filter) {
      synchronized (myLock) {
        myDirty = true;
        myFilters.remove(filter);
        myContinuationPoints.clear();
      }
    }

    public void addStartingPoint(String ref) {
      synchronized (myLock) {
        myDirty = true;
        myStartingPoints.add(ref);
        myContinuationPoints.clear();
      }
    }

    public void removeStartingPoint(String ref) {
      synchronized (myLock) {
        myDirty = true;
        myStartingPoints.remove(ref);
        myContinuationPoints.clear();
      }
    }

    public void updateExcludePoints(List<String> points) {
      synchronized (myLock) {
        myDirty = true;
        myExcludePoints = points;
        myContinuationPoints.clear();
      }
    }

    @Override
    public void markDirty() {
      synchronized (myLock) {
        myDirty = true;
      }
    }

    @Nullable
    public Collection<String> getStartingPoints() {
      synchronized (myLock) {
        return myStartingPoints;
      }
    }

    public Collection<ChangesFilter.Filter> getFilters() {
      synchronized (myLock) {
        return myFilters;
      }
    }

    public boolean isNothingSelected() {
      synchronized (myLock) {
        return ((myExcludePoints == null) || (myExcludePoints.isEmpty())) && myFilters.isEmpty() && myStartingPoints.isEmpty();
      }
    }
  }
}
