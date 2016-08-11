/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.util.BeforeAfter;
import com.intellij.util.PlusMinusModify;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChangesDelta {
  private final PlusMinusModify<BaseRevision> myDeltaListener;
  private boolean myInitialized;

  public ChangesDelta(final PlusMinusModify<BaseRevision> deltaListener) {
    myDeltaListener = deltaListener;
  }

  // true -> something changed
  public boolean step(final ChangeListsIndexes was, final ChangeListsIndexes became) {
    List<BaseRevision> wasAffected = was.getAffectedFilesUnderVcs();
    if (! myInitialized) {
      sendPlus(wasAffected);
      myInitialized = true;
      return true;  //+-
    }

    final Set<BaseRevision> toRemove = new HashSet<>();
    final Set<BaseRevision> toAdd = new HashSet<>();
    final Set<BeforeAfter<BaseRevision>> toModify = new HashSet<>();
    was.getDelta(became, toRemove, toAdd, toModify);

    for (BaseRevision pair : toRemove) {
      myDeltaListener.minus(pair);
    }
    sendPlus(toAdd);
    for (BeforeAfter<BaseRevision> beforeAfter : toModify) {
      myDeltaListener.modify(beforeAfter.getBefore(), beforeAfter.getAfter());
    }
    return ! toRemove.isEmpty() || ! toAdd.isEmpty();
  }

  private void sendPlus(final Collection<BaseRevision> toAdd) {
    if (toAdd != null) {
      for (BaseRevision pair : toAdd) {
        myDeltaListener.plus(pair);
      }
    }
  }
}
