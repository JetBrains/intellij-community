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

package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for selecting a version number valid for a specified file placed under
 * version control.
 *
 * @see com.intellij.openapi.vcs.AbstractVcs#getRevisionSelector() 
 * @since 5.0.2
 */
public interface RevisionSelector {
  /**
   * Shows the UI for selecting the version number and returns the selected
   * version number or null if the selection was cancelled.
   *
   * @param file the file for which the version number is requested.
   * @return the version number or null.
   */
  @Nullable VcsRevisionNumber selectNumber(VirtualFile file); 
}
