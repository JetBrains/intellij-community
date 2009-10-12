/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A single update/integrate/status session.
 *
 * @see UpdateEnvironment#updateDirectories
 * @see UpdateSessionAdapter
 */
public interface UpdateSession {
  /**
   * Returns the list of exception objects representing the errors occurred during the update/integrate/status
   * operation, or an empty list if no errors have occurred.
   *
   * @return the list of errors.
   */
  @NotNull
  List<VcsException> getExceptions();

  /**
   * Called when the VFS refresh of the files affected by an update/integrate operation is complete. Can be used,
   * for example, to show a merge dialog for files which have been merged with conflicts.
   */
  void onRefreshFilesCompleted();

  /**
   * Checks if the update/integrate/status information was cancelled by the user.
   *
   * @return true if the operation was cancelled, false otherwise.
   */
  boolean isCanceled();
}
