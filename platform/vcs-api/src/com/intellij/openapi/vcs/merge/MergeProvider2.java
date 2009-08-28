package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Merge provider which allows plugging into the functionality of the Multiple File Merge dialog.
 *
 * @author yole
 * @since 8.1
 */
public interface MergeProvider2 extends MergeProvider {


  /**
   * Initiates a multiple file merge operation for the specified list of files.
   *
   * @param files the list of files to be merged.
   * @return the merge session instance.
   */
  @NotNull
  MergeSession createMergeSession(List<VirtualFile> files);

}
