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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * Use this interface to access information available in the VCS Log.
 */
public interface VcsLog {

  /**
   * Returns commits currently selected in the log.
   */
  @NotNull
  List<Hash> getSelectedCommits();

  /**
   * Returns details of the selected commits if all of them have been loaded.
   * To avoid data inconsistency, if at least one of the selected commits have no details loaded, empty list is returned.
   */
  @NotNull
  List<VcsFullCommitDetails> getSelectedDetails();

  /**
   * Returns details of the given commit, if they have been already loaded.
   * In most cases they are already in the cache, and will be returned.
   * Otherwise null is returned.
   * Asynchronous loading of the details which are not yet available is done automatically from the log table component.
   */
  @Nullable
  VcsFullCommitDetails getDetailsIfAvailable(@NotNull Hash hash);

  /**
   * Returns names of branches which contain the given commit, or null if this information is unavailable.
   */
  @Nullable
  Collection<String> getContainingBranches(@NotNull Hash commitHash);

  /**
   * Returns all {@link VcsRef commit references} available in the log.
   */
  @NotNull
  Collection<VcsRef> getAllReferences();

  /**
   * Selects the commit node defined by the given reference (commit hash, branch or tag).
   */
  void jumpToReference(String reference);

  /**
   * Returns the VCS log toolbar component.
   */
  @NotNull
  Component getToolbar();

  /**
   * Returns {@link VcsLogProvider VcsLogProviders} which are active in this log, i.e. which VCS roots are shown in the log.
   */
  @NotNull
  Collection<VcsLogProvider> getLogProviders();

}
