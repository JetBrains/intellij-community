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

package org.zmlx.hg4idea.repo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;


/**
 * <p>
 * HgRepository is a representation of a Hg repository stored under the specified directory.
 * It stores the information about the repository, which is frequently requested by other plugin components.
 * All get-methods (like {@link #getCurrentRevision()}) are just getters of the correspondent fields and thus are very fast.
 * </p>
 * <p>
 * The HgRepository is updated "externally" by the {@link  org.zmlx.hg4idea.repo.HgRepositoryUpdater}, when correspondent {@code .hg/} service files
 * change.
 * </p>
 * Other components may subscribe to HgRepository changes via the STATUS_TOPIC {@link com.intellij.util.messages.Topic}
 * </p>
 *
 * @author Nadya Zabrodina
 */

public interface HgRepository {

  String DEFAULT_BRANCH = "default";

  /**
   * Current state of the repository.
   */

  enum State {
    /**
     * HEAD is on branch, no merge process is in progress.
     */

    NORMAL,
    /**
     * During merge (for instance, merge failed with conflicts that weren't immediately resolved).
     */

    MERGING {
      @Override
      public String toString() {
        return "Merging";
      }
    },

  }

  @NotNull
  VirtualFile getRoot();

  @NotNull
  VirtualFile getHgDir();

  @NotNull
  String getPresentableUrl();

  @NotNull
  Project getProject();


  @NotNull
  State getState();


  /**
   * Returns the hash of the revision, which HEAD currently points to.
   * Returns null only in the case of a fresh repository, when no commit have been made.
   */

  @Nullable
  String getCurrentRevision();

  /**
   * Returns the current branch of this Hg repository.
   * If the repository is being rebased, then the current branch is the branch being rebased (which was current before the rebase
   * operation has started).
   * Returns null, if the repository is not on a branch and not in the REBASING state.
   */

  @NotNull
  String getCurrentBranch();

  @NotNull
  Collection<String> getBranches();

  /**
   * @return true if current repository is "fresh", i.e. if no commits have been made yet.
   */

  boolean isFresh();

  /**
   * Updates the HgRepository by reading information from .hg dir...
   */

  void update();
}
