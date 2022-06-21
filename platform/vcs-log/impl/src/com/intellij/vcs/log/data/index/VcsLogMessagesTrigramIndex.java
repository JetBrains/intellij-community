// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.VoidDataExternalizer;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.impl.VcsLogErrorHandler;
import com.intellij.vcs.log.util.StorageId;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public final class VcsLogMessagesTrigramIndex extends VcsLogFullDetailsIndex<Void, VcsCommitMetadata> {
  @NonNls private static final String TRIGRAMS = "trigrams";

  public VcsLogMessagesTrigramIndex(@NotNull StorageId storageId,
                                    @Nullable StorageLockContext storageLockContext,
                                    @NotNull VcsLogErrorHandler errorHandler,
                                    @NotNull Disposable disposableParent) throws IOException {
    super(storageId, TRIGRAMS, new TrigramMessageIndexer(), VoidDataExternalizer.INSTANCE,
          storageLockContext, errorHandler, disposableParent);
  }

  @Nullable
  public IntSet getCommitsForSubstring(@NotNull CharSequence string) throws StorageException {
    IntSet trigrams = TrigramBuilder.getTrigrams(string);
    return trigrams.isEmpty() ? null : getCommitsWithAllKeys(trigrams);
  }

  public static final class TrigramMessageIndexer implements DataIndexer<Integer, Void, VcsCommitMetadata> {
    @NotNull
    @Override
    public Map<Integer, Void> map(@NotNull VcsCommitMetadata inputData) {
      return TrigramBuilder.getTrigramsAsMap(inputData.getFullMessage());
    }
  }
}
