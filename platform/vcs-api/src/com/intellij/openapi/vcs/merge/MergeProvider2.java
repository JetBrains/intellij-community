// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Merge provider which allows plugging into the functionality of the Multiple File Merge dialog.
 */
public interface MergeProvider2 extends MergeProvider {


  /**
   * Initiates a multiple file merge operation for the specified list of files.
   *
   * @param files the list of files to be merged.
   * @return the merge session instance.
   */
  @NotNull
  MergeSession createMergeSession(@NotNull List<VirtualFile> files);

}
