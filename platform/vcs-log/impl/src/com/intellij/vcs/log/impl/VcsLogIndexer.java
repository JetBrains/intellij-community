// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.VcsCommitMetadata;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface VcsLogIndexer {
  /**
   * Reads full details for specified commits in the repository.
   * Reports the commits to the consumer to avoid creation & even temporary storage of a too large commits collection.
   */
  void readFullDetails(@NotNull VirtualFile root, @NotNull List<String> hashes,
                       @NotNull VcsLogIndexer.PathsEncoder encoder,
                       @NotNull Consumer<? super CompressedDetails> commitConsumer)
    throws VcsException;

  /**
   * Reads full details of all commits in the repository.
   * <p/>
   * Reports the commits to the consumer to avoid creation & even temporary storage of a too large commits collection.
   */
  void readAllFullDetails(@NotNull VirtualFile root, @NotNull VcsLogIndexer.PathsEncoder encoder,
                          @NotNull Consumer<? super CompressedDetails> commitConsumer) throws VcsException;

  @NotNull
  VcsKey getSupportedVcs();

  interface CompressedDetails extends VcsCommitMetadata {
    @NotNull
    Int2ObjectMap<Change.Type> getModifiedPaths(int parent);

    @NotNull
    Int2IntMap getRenamedPaths(int parent);
  }

  interface PathsEncoder {
    int encode(@NotNull VirtualFile root, @NotNull String relativePath, boolean isDirectory) throws VcsException;
  }
}
