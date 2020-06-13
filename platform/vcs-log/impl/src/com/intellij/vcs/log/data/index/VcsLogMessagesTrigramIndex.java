// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.VoidDataExternalizer;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.util.StorageId;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public final class VcsLogMessagesTrigramIndex extends VcsLogFullDetailsIndex<Void, VcsCommitMetadata> {
  private static final String TRIGRAMS = "trigrams"; // NON-NLS

  public VcsLogMessagesTrigramIndex(@NotNull StorageId storageId,
                                    @NotNull FatalErrorHandler fatalErrorHandler,
                                    @NotNull Disposable disposableParent) throws IOException {
    super(storageId, TRIGRAMS, new TrigramMessageIndexer(), VoidDataExternalizer.INSTANCE,
          fatalErrorHandler, disposableParent);
  }

  @Nullable
  public IntSet getCommitsForSubstring(@NotNull CharSequence string) throws StorageException {
    MyTrigramProcessor trigramProcessor = new MyTrigramProcessor();
    TrigramBuilder.processTrigrams(string, trigramProcessor);

    if (trigramProcessor.map.isEmpty()) {
      return null;
    }
    return getCommitsWithAllKeys(trigramProcessor.map.keySet());
  }

  public static final class TrigramMessageIndexer implements DataIndexer<Integer, Void, VcsCommitMetadata> {
    @NotNull
    @Override
    public Map<Integer, Void> map(@NotNull VcsCommitMetadata inputData) {
      MyTrigramProcessor trigramProcessor = new MyTrigramProcessor();
      TrigramBuilder.processTrigrams(inputData.getFullMessage(), trigramProcessor);
      return trigramProcessor.map;
    }
  }

  private static final class MyTrigramProcessor extends TrigramBuilder.TrigramProcessor {
    Int2ObjectMap<Void> map;

    @Override
    public boolean consumeTrigramsCount(int count) {
      map = new Int2ObjectOpenHashMap<>(count);
      return true;
    }

    @Override
    public boolean test(int value) {
      map.put(value, null);
      return true;
    }
  }
}
