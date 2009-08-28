package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.ColumnInfo;

/**
 * Represents the state of a multiple file merge operation.
 *
 * @author yole
 * @see com.intellij.openapi.vcs.merge.MergeProvider2#createMergeSession
 * @since 8.1
 */
public interface MergeSession {
  enum Resolution {
    Merged, AcceptedYours, AcceptedTheirs
  }

  /**
   * Returns the list of additional columns to be displayed in the dialog. The Item type for the
   * column should be VirtualFile.
   *
   * @return the list of columns, or an empty list if no additional columns should be displayed.
   */
  ColumnInfo[] getMergeInfoColumns();

  /**
   * Returns true if a merge operation can be invoked for the specified virtual file, false otherwise.
   *
   * @param file a file shown in the dialog.
   * @return true if the merge dialog can be shown, false otherwise.
   */
  boolean canMerge(VirtualFile file);

  /**
   * Called when the user executes one of the resolve actions (merge, accept yours, accept theirs) for
   * a conflicting file. Note that if canMerge() is false for the file, the nothing is done by the idea,
   * the implementer should perform necessary actions for conflict resolution itself.
   *
   * @param file       the conflicting file.
   * @param resolution the used resolution.
   */
  void conflictResolvedForFile(VirtualFile file, Resolution resolution);

}
