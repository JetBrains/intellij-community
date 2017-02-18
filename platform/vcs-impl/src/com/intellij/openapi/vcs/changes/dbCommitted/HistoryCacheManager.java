/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.dbCommitted;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.committed.CachesHolder;
import com.intellij.openapi.vcs.changes.committed.RepositoryLocationCache;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.PairProcessor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/8/12
 * Time: 7:01 PM
 */
public class HistoryCacheManager {
  private final Project myProject;
  private final BackgroundTaskQueue myQueue;
  private RepositoryLocationCache myRepositoryLocationCache;
  private CachesHolder myCachesHolder;
  private final KnownRepositoryLocations myKnownRepositoryLocations;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.dbCommitted.HistoryCacheManager");
  private VcsSqliteLayer myDbUtil;

  public HistoryCacheManager(Project project) {
    myProject = project;
    myQueue = new BackgroundTaskQueue(myProject, "VCS project history cache update");
    myKnownRepositoryLocations = new KnownRepositoryLocations();
    myRepositoryLocationCache = new RepositoryLocationCache(myProject);
    myCachesHolder = new CachesHolder(myProject, myRepositoryLocationCache);
    myDbUtil = new VcsSqliteLayer(myProject, myKnownRepositoryLocations); // does not create connection immediately
  }

  public void initIfNeeded() {
    myQueue.run(new CreateInitDatabase());
  }

  public static List<AbstractVcs> getGoodActiveVcses(final Project project) {
    final AbstractVcs[] abstractVcses = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    final List<AbstractVcs> result = new SmartList<>();
    for(AbstractVcs vcs: abstractVcses) {
      if (vcs.getCommittedChangesProvider() instanceof CachingCommittedChangesProvider && VcsType.centralized.equals(vcs.getType())) {
        result.add(vcs);
      }
    }
    return result;
  }

  public void appendChanges(final AbstractVcs vcs, final String root, final List<CommittedChangeList> lists) {
    myQueue.run(new AppendChanges(vcs, root, lists));
  }

  public List<CommittedChangeList> readListsByDates(final AbstractVcs vcs, final RepositoryLocation location,
                                                    final long lastTs, final long oldTs, final String subfolder) throws VcsException {
    return myDbUtil.readLists(vcs, location, RevisionId.createTime(lastTs), RevisionId.createTime(oldTs), subfolder);
  }

  public List<CommittedChangeList> readLists(final AbstractVcs vcs, final RepositoryLocation location, final long lastRev, final long oldRev)
    throws VcsException {
    return myDbUtil.readLists(vcs, location, lastRev, oldRev);
  }

  public long getLastRevision(final AbstractVcs vcs, final RepositoryLocation location) {
    return myDbUtil.getLastRevision(vcs, location2string(location)).getNumber();
  }

  private String location2string(RepositoryLocation location) {
    return FileUtil.toSystemIndependentName(location.toPresentableString());
  }

  public long getFirstRevision(final AbstractVcs vcs, final RepositoryLocation location) {
    return myDbUtil.getFirstRevision(vcs, location2string(location)).getNumber();
  }

  public PathState getPathState(final AbstractVcs vcs, final RepositoryLocation location, final String path) throws VcsException {
    return myDbUtil.getPathState(vcs, location, path);
  }

  private class AppendChanges extends Task.Backgroundable {
    private final List<CommittedChangeList> myLists;
    private final String myRoot;
    private final AbstractVcs myVcs;

    private VcsException myException;

    private AppendChanges(final AbstractVcs vcs, final String root, final List<CommittedChangeList> lists) {
      super(HistoryCacheManager.this.myProject, "Append data to history caches", false);
      myLists = lists;
      myVcs = vcs;
      myRoot = FileUtil.toSystemIndependentName(root);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        myDbUtil.appendLists(myVcs, myRoot, myLists);
      }
      catch (VcsException e) {
        myException = e;
      }
    }

    @Override
    public void onSuccess() {
      if (myException != null) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          throw new RuntimeException(myException);
        }
        VcsBalloonProblemNotifier.showOverChangesView(myProject, myException.getMessage(), MessageType.ERROR);
      }
    }
  }

  private class CreateInitDatabase extends Task.Backgroundable {
    private VcsException myException;

    public CreateInitDatabase() {
      super(HistoryCacheManager.this.myProject, "Update VCS and roots data", false);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        //indicator.setText2("Checking and possibly creating database");
        indicator.setText2("Updating VCS and roots");
        final MultiMap<String, String> map = new MultiMap<>();
        myCachesHolder.iterateAllRepositoryLocations(new PairProcessor<RepositoryLocation, AbstractVcs>() {
          @Override
          public boolean process(RepositoryLocation location, AbstractVcs vcs) {
            map.putValue(vcs.getName(), location2string(location));
            return true;
          }
        });
        myDbUtil.checkVcsRootsAreTracked(map);
      }
      catch (VcsException e) {
        LOG.info(e);
        myException = e;
      }
    }

    @Override
    public void onSuccess() {
      // todo track whether the db was initialized, if not - delete all other requests
      if (myException != null) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          throw new RuntimeException(myException);
        }
        VcsBalloonProblemNotifier.showOverChangesView(myProject, myException.getMessage(), MessageType.ERROR);
      }
    }
  }
}
