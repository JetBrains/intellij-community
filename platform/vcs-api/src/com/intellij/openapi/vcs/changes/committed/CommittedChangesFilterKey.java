/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import org.jetbrains.annotations.NotNull;

/**
* @author irengrig
*/
public class CommittedChangesFilterKey implements Comparable<CommittedChangesFilterKey> {
  private final CommittedChangesFilterPriority myPriority;
  @NotNull
  private final String myId;

  public CommittedChangesFilterKey(@NotNull final String id, final CommittedChangesFilterPriority priority) {
    myId = id;
    myPriority = priority;
  }

  @Override
  public int compareTo(final CommittedChangesFilterKey o) {
    final int comp = myPriority.getPriority() - o.myPriority.getPriority();
    return comp < 0 ? -1 : (comp == 0 ? 0 : 1);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final CommittedChangesFilterKey key = (CommittedChangesFilterKey)o;

    if (myPriority.getPriority() != key.myPriority.getPriority()) return false;
    if (!myId.equals(key.myId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPriority.getPriority();
    result = 31 * result + myId.hashCode();
    return result;
  }
}
