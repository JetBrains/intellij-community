// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface VcsLogIndexer {
  /**
   * Reads full details for specified commits in the repository.
   * Reports commits to the consumer to avoid creation & even temporary storage of a too large commits collection.
   * Allows to skip full rename detection to make things faster. For git, for example, this would be adding diff.renameLimit=x to the command.
   */
  void readFullDetails(@NotNull VirtualFile root, @NotNull List<String> hashes,
                       @NotNull Consumer<? super VcsFullCommitDetails> commitConsumer,
                       boolean fast)
    throws VcsException;

  /**
   * Reads full details of all commits in the repository.
   * <p/>
   * Reports commits to the consumer to avoid creation & even temporary storage of a too large commits collection.
   */
  void readAllFullDetails(@NotNull VirtualFile root, @NotNull Consumer<? super VcsFullCommitDetails> commitConsumer) throws VcsException;

  @NotNull
  VcsKey getSupportedVcs();
}
