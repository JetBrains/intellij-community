// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Defines a search engine which will be used to find results in "Find in Path" and "Replace in Path" actions.
 * Several search engines can be used at the same moment to achieve best performance (time-to-result).
 */
@ApiStatus.Experimental
public interface FindInProjectSearchEngine {
  @ApiStatus.Internal
  ExtensionPointName<FindInProjectSearchEngine> EP_NAME = ExtensionPointName.create("com.intellij.findInProjectSearchEngine");

  /**
   * Constructs a searcher for a given {@param findModel} which serves as a input query.
   */
  @Nullable
  FindInProjectSearcher createSearcher(@NotNull FindModel findModel, @NotNull Project project);

  @ApiStatus.Experimental
  interface FindInProjectSearcher {
    /**
     * @return files that contain non-trivial search results for corresponding {@link FindModel}.
     * Returned files are _likely_ contain occurrences of the query, but it's not 100% guaranteed, so additional check may be needed
     */
    @NotNull
    Collection<VirtualFile> searchForOccurrences();

    /**
     * @return true if there are no occurrences can be found outside the result of {@link FindInProjectSearcher#searchForOccurrences()},
     * <p>
     * More specifically: if this method returns true, and {@link #searchForOccurrences()} does NOT return file X, and
     * {@code isCovered(X)==true} => file X is guaranteed to NOT contain a search pattern.
     * If this method returns false, then even if {@code isCovered(X)==true} and {@link #searchForOccurrences()} does NOT return
     * the file X -- it is still possible that the file X contains a search pattern.
     */
    boolean isReliable();

    /**
     * Returns true if {@param file} is a part of "indexed" scope of corresponding search engine and no need to open file's content to find a query,
     * otherwise false.
     * <p>
     * Called only in case when searcher is not reliable (see {@link FindInProjectSearcher#isReliable()}).
     */
    boolean isCovered(@NotNull VirtualFile file);
  }
}