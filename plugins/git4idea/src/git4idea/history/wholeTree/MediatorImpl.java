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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.AbstractCalledLater;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.history.browser.ChangesFilter;
import git4idea.history.browser.SymbolicRefs;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author irengrig
 */
public class MediatorImpl implements Mediator {
  private final Ticket myTicket;
  private final Project myProject;
  private BigTableTableModel myTableModel;
  private UIRefresh myUIRefresh;
  private Loader myLoader;
  private final ModalityState myState;

  public MediatorImpl(final Project project, final ModalityState state) {
    myProject = project;
    myState = state;
    myTicket = new Ticket();
  }

  @Override
  public boolean appendResult(final Ticket ticket, final List<CommitI> result,
                              final @Nullable List<List<AbstractHash>> parents) {
    if (! myTicket.equals(ticket)) {
      return false;
    }

    new AbstractCalledLater(myProject, myState) {
      @Override
      public void run() {
        if (! myTicket.equals(ticket)) return;
        myTableModel.appendData(result, parents);
        myUIRefresh.linesReloaded();
      }
    }.callMe();
    return true;
  }

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

  @Override
  public void acceptException(VcsException e) {
    myUIRefresh.acceptException(e);
  }

  @Override
  public void reload(final RootsHolder rootsHolder,
                     final Collection<String> startingPoints,
                     final Collection<ChangesFilter.Filter> filters,
                     String[] possibleHashes) {
    myTicket.increment();
    myTableModel.clear();
    myLoader.loadSkeleton(myTicket.copy(), rootsHolder, startingPoints, filters, possibleHashes);
  }

  public void setLoader(Loader loader) {
    myLoader = loader;
  }

  public void setTableModel(BigTableTableModel tableModel) {
    myTableModel = tableModel;
  }

  public void setUIRefresh(UIRefresh UIRefresh) {
    myUIRefresh = UIRefresh;
  }
}
