/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.VoidDataExternalizer;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.util.StorageId;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

@NonNls
public class VcsLogMessagesTrigramIndex extends VcsLogFullDetailsIndex<Void, VcsCommitMetadata> {
  public static final String TRIGRAMS = "trigrams"; // NON-NLS

  public VcsLogMessagesTrigramIndex(@NotNull StorageId storageId,
                                    @NotNull FatalErrorHandler fatalErrorHandler,
                                    @NotNull Disposable disposableParent) throws IOException {
    super(storageId, TRIGRAMS, new TrigramMessageIndexer(), VoidDataExternalizer.INSTANCE,
          fatalErrorHandler, disposableParent);
  }

  @Nullable
  public TIntHashSet getCommitsForSubstring(@NotNull String string) throws StorageException {
    MyTrigramProcessor trigramProcessor = new MyTrigramProcessor();
    TrigramBuilder.processTrigrams(string, trigramProcessor);

    if (trigramProcessor.map.isEmpty()) return null;

    return getCommitsWithAllKeys(trigramProcessor.map.keySet());
  }

  public static class TrigramMessageIndexer implements DataIndexer<Integer, Void, VcsCommitMetadata> {
    @NotNull
    @Override
    public Map<Integer, Void> map(@NotNull VcsCommitMetadata inputData) {
      MyTrigramProcessor trigramProcessor = new MyTrigramProcessor();
      TrigramBuilder.processTrigrams(inputData.getFullMessage(), trigramProcessor);

      return trigramProcessor.map;
    }
  }

  private static class MyTrigramProcessor extends TrigramBuilder.TrigramProcessor {
    Map<Integer, Void> map;

    @Override
    public boolean consumeTrigramsCount(int count) {
      map = new THashMap<>(count);
      return true;
    }

    @Override
    public boolean execute(int value) {
      map.put(value, null);
      return true;
    }
  }
}
