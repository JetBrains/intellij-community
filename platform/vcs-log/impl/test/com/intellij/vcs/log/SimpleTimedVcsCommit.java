/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SimpleTimedVcsCommit implements TimedVcsCommit {

  private final Hash myHash;
  private final List<Hash> myParents;
  private final long myTime;

  public SimpleTimedVcsCommit(Hash commitHash, List<Hash> parentHashes, long time) {
    myHash = commitHash;
    myParents = parentHashes;
    myTime = time;
  }

  @Override
  public long getTime() {
    return myTime;
  }

  @NotNull
  @Override
  public Hash getHash() {
    return myHash;
  }

  @NotNull
  @Override
  public List<Hash> getParents() {
    return myParents;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SimpleTimedVcsCommit commit = (SimpleTimedVcsCommit)o;

    if (myHash != null ? !myHash.equals(commit.myHash) : commit.myHash != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myHash != null ? myHash.hashCode() : 0;
  }
}
