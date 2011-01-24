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
import git4idea.history.NewGitUsersComponent;
import git4idea.history.browser.ChangesFilter;

import java.util.*;

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
                           final Collection<Collection<ChangesFilter.Filter>> filters,
                           String[] possibleHashes,
                           final LoadGrowthController loadGrowthController) {
    if (myPreviousAlgorithm != null) {
      myPreviousAlgorithm.stop();
    }
    final List<LoaderAndRefresher<CommitHashPlusParents>> list = new ArrayList<LoaderAndRefresher<CommitHashPlusParents>>();
    final List<VirtualFile> roots = rootsHolder.getRoots();
    int i = 0;
    for (VirtualFile root : roots) {
      final LoaderAndRefresherImpl.MyRootHolder rootHolder = roots.size() == 1 ?
        new LoaderAndRefresherImpl.OneRootHolder(root) :
        new LoaderAndRefresherImpl.ManyCaseHolder(i, rootsHolder);

      if (filters.isEmpty()) {
        final LoaderAndRefresherImpl loaderAndRefresher =
        new LoaderAndRefresherImpl(ticket, Collections.<ChangesFilter.Filter>emptyList(), myMediator, startingPoints, myDetailsCache,
                                   myProject, rootHolder, myUsersIndex, loadGrowthController.getId());
        list.add(loaderAndRefresher);
      } else {
        Collection<Collection<ChangesFilter.Filter>> reordered = new ArrayList<Collection<ChangesFilter.Filter>>();
        final Iterator<Collection<ChangesFilter.Filter>> iterator = filters.iterator();
        if (iterator.hasNext()) {
          final Collection<ChangesFilter.Filter> first = iterator.next();
          for (ChangesFilter.Filter filter : first) {
            final ArrayList<ChangesFilter.Filter> newList = new ArrayList<ChangesFilter.Filter>();
            newList.add(filter);
            reordered.add(newList);
          }
        }
        while (iterator.hasNext()) {
          final Collection<ChangesFilter.Filter> next = iterator.next();
          final Collection<Collection<ChangesFilter.Filter>> reorderedCopy = reordered;
          reordered = new ArrayList<Collection<ChangesFilter.Filter>>();
          for (ChangesFilter.Filter filter : next) {
            for (Collection<ChangesFilter.Filter> filterCollection : reorderedCopy) {
              final ArrayList<ChangesFilter.Filter> newList = new ArrayList<ChangesFilter.Filter>(filterCollection);
              newList.add(filter);
              reordered.add(newList);
            }
          }
        }

        for (Collection<ChangesFilter.Filter> filterCollection : reordered) {
          final LoaderAndRefresherImpl loaderAndRefresher =
          new LoaderAndRefresherImpl(ticket, filterCollection, myMediator, startingPoints, myDetailsCache, myProject, rootHolder, myUsersIndex,
                                     loadGrowthController.getId());
          list.add(loaderAndRefresher);
        }
      }
      ++ i;
    }

    myUsersComponent.acceptUpdate(myUsersIndex.getKeys());

    //final List<String> abstractHashs = possibleHashes == null ? null : filterNumbers(possibleHashes);
    myPreviousAlgorithm = new LoadAlgorithm(myProject, list, possibleHashes == null ? null : Arrays.asList(possibleHashes));
    myPreviousAlgorithm.execute();
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
