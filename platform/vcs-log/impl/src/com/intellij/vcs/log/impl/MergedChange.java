/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A special change representing a change in a merge commit.
 * It contains a list of changes to each parent that were merged.
 */
@ApiStatus.Internal
public abstract class MergedChange extends Change {
  public MergedChange(@NotNull Change change) {
    super(change.getBeforeRevision(), change.getAfterRevision(), change.getFileStatus());
  }

  /**
   * Returns a list of changes that were merged in a merge commit.
   */
  public abstract List<Change> getSourceChanges();

  /**
   * A simple implementation with a merge change and a list of source changes inside.
   */
  public static class SimpleMergedChange extends MergedChange {
    private final List<Change> mySourceChanges;

    public SimpleMergedChange(@NotNull Change mergedChange, @NotNull List<Change> sourceChanges) {
      super(mergedChange);
      mySourceChanges = sourceChanges;
    }

    @Override
    public List<Change> getSourceChanges() {
      return mySourceChanges;
    }
  }
}
