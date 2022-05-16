/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface BranchRenameListener extends EventListener {

  Topic<BranchRenameListener> VCS_BRANCH_RENAMED = Topic.create("VCS branch renamed", BranchRenameListener.class);

  /**
   * Invoked when a branch has been renamed.
   * @param oldName name before renaming
   * @param newName name after renaming
   */
  void branchNameChanged(@NotNull String oldName, @NotNull String newName);

  /**
   * Invoked when a branch rename has been rolled back.
   * @param oldName name to which the rollback rename was made
   * @param newName name that was before the rolled back renaming
   */
  void branchNameRollback(@NotNull String oldName, @NotNull String newName);
}
