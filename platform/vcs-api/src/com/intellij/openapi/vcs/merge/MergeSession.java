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
package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the state of a multiple file merge operation.
 *
 * @see MergeProvider2#createMergeSession
 */
public interface MergeSession {
  enum Resolution {
    Merged, AcceptedYours, AcceptedTheirs
  }

  /**
   * Returns the list of additional columns to be displayed in the dialog. The Item type for the
   * column should be VirtualFile.
   *
   * @return the array of columns, or an empty array if no additional columns should be displayed.
   */
  ColumnInfo @NotNull [] getMergeInfoColumns();

  /**
   * Returns true if the given virtual file can be merged by its content.
   * <br/><br/>
   * It means that the Merge dialog can be shown for this file, and Accept Yours/Theirs can be called on this file.
   * <br/><br/>
   * Note that {@link MergeSessionEx} can be used to Accept Yours/Theirs via a custom procedure,
   * for example, via calling a VCS command. In this case this flag is ignored for Accept Yours/Theirs functionality,
   * but it still allows or disallows to press Merge and show the Merge dialog.
   *
   * @param file a file with conflicts shown in the dialog.
   * @return true if the merge dialog can be shown for this file, false otherwise.
   */
  boolean canMerge(@NotNull VirtualFile file);

  /**
   * Called when the user executes one of the resolve actions (merge, accept yours, accept theirs) for
   * a conflicting file. Note that if canMerge() is false for the file, the nothing is done by the idea,
   * the implementer should perform necessary actions for conflict resolution itself.
   *
   * @param file       the conflicting file.
   * @param resolution the used resolution.
   */
  void conflictResolvedForFile(@NotNull VirtualFile file, @NotNull Resolution resolution);
}
