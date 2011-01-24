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

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.history.browser.ChangesFilter;
import git4idea.history.browser.SymbolicRefs;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author irengrig
 */
public interface Mediator {
  void reload(RootsHolder rootsHolder,
              final Collection<String> startingPoints,
              final Collection<Collection<ChangesFilter.Filter>> filters,
              @Nullable String[] possibleHashes);

  /**
   * @return false -> ticket already changed
   */
  StepType appendResult(final Ticket ticket,
                        final List<CommitI> result,
                        @Nullable final List<List<AbstractHash>> parents,
                        LoadGrowthController.ID id);

  void reportSymbolicRefs(final Ticket ticket, VirtualFile root, final SymbolicRefs symbolicRefs);

  // does not change ticket...
  void continueLoading();
  void forceStop();

  void acceptException(final VcsException e);

  void oneFinished();

  final class Ticket {
    private int myId;

    public Ticket() {
      myId = 0;
    }

    private Ticket(int id) {
      myId = id;
    }

    public Ticket copy() {
      return new Ticket(myId);
    }

    public void increment() {
      ++ myId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Ticket ticket = (Ticket)o;

      if (myId != ticket.myId) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myId;
    }
  }
}
