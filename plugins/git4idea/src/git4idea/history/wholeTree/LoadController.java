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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.continuation.Continuation;
import git4idea.history.NewGitUsersComponent;
import git4idea.history.browser.ChangesFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author irengrig
 */
public class LoadController implements Loader {
  private final UsersIndex myUsersIndex;
  private final Project myProject;
  private final ModalityState myState;
  private final Mediator myMediator;
  private final DetailsCache myDetailsCache;
  private LoadAlgorithm myPreviousAlgorithm;
  private NewGitUsersComponent myUsersComponent;

  public LoadController(final Project project, final ModalityState state, final Mediator mediator, final DetailsCache detailsCache) {
    myProject = project;
    myState = state;
    myMediator = mediator;
    myDetailsCache = detailsCache;
    myUsersIndex = new UsersIndex();
    myUsersComponent = NewGitUsersComponent.getInstance(myProject);
  }

  @CalledInAwt
  @Override
  public void loadSkeleton(final Mediator.Ticket ticket,
                           final RootsHolder rootsHolder,
                           final Collection<String> startingPoints,
                           final GitLogFilters filters,
                           final LoadGrowthController loadGrowthController) {
    if (myPreviousAlgorithm != null) {
      myPreviousAlgorithm.stop();
    }
    final List<LoaderAndRefresher<CommitHashPlusParents>> list = new ArrayList<LoaderAndRefresher<CommitHashPlusParents>>();
    final List<ByRootLoader> shortLoaders = new ArrayList<ByRootLoader>();
    final List<VirtualFile> roots = rootsHolder.getRoots();
    int i = 0;
    for (VirtualFile root : roots) {
      final LoaderAndRefresherImpl.MyRootHolder rootHolder = roots.size() == 1 ?
        new LoaderAndRefresherImpl.OneRootHolder(root) :
        new LoaderAndRefresherImpl.ManyCaseHolder(i, rootsHolder);

      filters.callConsumer(new Consumer<List<ChangesFilter.Filter>>() {
        @Override
        public void consume(final List<ChangesFilter.Filter> filters) {
          final LoaderAndRefresherImpl loaderAndRefresher =
          new LoaderAndRefresherImpl(ticket, filters, myMediator, startingPoints, myDetailsCache, myProject, rootHolder, myUsersIndex,
                                     loadGrowthController.getId());
          list.add(loaderAndRefresher);
        }
      }, true);

      shortLoaders.add(new ByRootLoader(myProject, rootHolder, myMediator, myDetailsCache, ticket, myUsersIndex, filters, startingPoints));
      ++ i;
    }

    myUsersComponent.acceptUpdate(myUsersIndex.getKeys());

    if (myPreviousAlgorithm != null) {
      final Continuation oldContinuation = myPreviousAlgorithm.getContinuation();
      oldContinuation.cancelCurrent();
      oldContinuation.clearQueue();
    }

    final Continuation continuation = Continuation.createFragmented(myProject, true);
    myPreviousAlgorithm = new LoadAlgorithm(myProject, list, shortLoaders, continuation);
    myPreviousAlgorithm.fillContinuation();
    continuation.resume();
  }

  @Override
  public void resume() {
    assert myPreviousAlgorithm != null;
    myPreviousAlgorithm.resume();
  }

  private List<String> filterNumbers(final String[] s) {
    final List<String> result = new ArrayList<String>();
    for (String part : s) {
      if (s.length > 40) continue;
      final AbstractHash abstractHash = AbstractHash.createStrict(part);
      if (abstractHash != null) result.add(part);
    }
    return result;
  }
}
