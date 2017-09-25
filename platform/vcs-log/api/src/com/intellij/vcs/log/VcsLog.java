/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Use this interface to access information available in the VCS Log.
 */
public interface VcsLog {

  /**
   * Returns commits currently selected in the log.
   */
  @NotNull
  List<CommitId> getSelectedCommits();

  /**
   * Returns short details of the selected commit which are visible in the table. <br/>
   * Short details are loaded faster than full details and since it is done while scrolling,
   * there is a better chance that details for a commit are loaded when user selects it.
   * This makes this method preferable to {@link #getSelectedDetails()}.
   * Still, check for LoadingDetails instance has to be done when using details from this list.
   */
  @NotNull
  List<VcsShortCommitDetails> getSelectedShortDetails();

  /**
   * Returns details of the selected commits.
   * For commits that are not loaded an instance of LoadingDetails is returned.
   */
  @NotNull
  List<VcsFullCommitDetails> getSelectedDetails();

  /**
   * Sends a request to load details that are currently selected.
   * Details are loaded in background. If a progress indicator is specified it is used during loading process.
   * After all details are loaded they are provided to the consumer in the EDT.
   *
   * @param consumer  called in EDT after all details are loaded.
   */
  void requestSelectedDetails(@NotNull Consumer<List<VcsFullCommitDetails>> consumer);

  /**
   * Returns names of branches which contain the given commit, or null if this information is unavailable yet.
   */
  @Nullable
  Collection<String> getContainingBranches(@NotNull Hash commitHash, @NotNull VirtualFile root);

  /**
   * Asynchronously selects the commit node defined by the given reference (commit hash, branch or tag).
   * Returns a {@link Future future} that allows to check if the commit was selected, wait for the selection while log is being loaded,
   * or cancel commit selection.
   */
  @NotNull
  Future<Boolean> jumpToReference(String reference);

  /**
   * Returns {@link VcsLogProvider VcsLogProviders} which are active in this log, i.e. which VCS roots are shown in the log.
   */
  @NotNull
  Map<VirtualFile, VcsLogProvider> getLogProviders();
}
