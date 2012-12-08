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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Ticket;
import git4idea.history.browser.CachedRefs;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author irengrig
 */
public interface Mediator {
  void reload(RootsHolder rootsHolder,
              final Collection<String> startingPoints,
              Collection<String> endPoints, @Nullable final GitLogFilters filters, final boolean topoOrder);

  /**
   * @return false -> ticket already changed
   */
  StepType appendResult(final Ticket ticket,
                        final List<CommitI> result,
                        @Nullable final List<List<AbstractHash>> parents, VirtualFile root, boolean checkForSequential);

  void reportSymbolicRefs(final Ticket ticket, VirtualFile root, final CachedRefs symbolicRefs);

  // does not change ticket...
  void continueLoading();
  void forceStop();

  void acceptException(final VcsException e);
  void acceptStashHead(final Ticket ticket, VirtualFile root, Pair<AbstractHash, AbstractHash> hash);

  void oneFinished();

  @CalledInAwt
  void reloadSetFixed(Map<AbstractHash, Long> starred, RootsHolder rootsHolder);
}
