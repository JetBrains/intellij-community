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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.impl.CollectionsDelta;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ChangesDelta {
  private final PlusMinus<Pair<String, AbstractVcs>> myDeltaListener;
  private ProjectLevelVcsManager myVcsManager;
  private boolean myInitialized;

  public ChangesDelta(final Project project, final PlusMinus<Pair<String, AbstractVcs>> deltaListener) {
    myDeltaListener = deltaListener;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
  }

  // true -> something changed
  public boolean step(final ChangeListsIndexes was, final ChangeListsIndexes became) {
    List<Pair<String, VcsKey>> wasAffected = was.getAffectedFilesUnderVcs();
    if (! myInitialized) {
      sendPlus(wasAffected);
      myInitialized = true;
      return true;  //+-
    }
    final List<Pair<String, VcsKey>> becameAffected = became.getAffectedFilesUnderVcs();

    final Set<Pair<String,VcsKey>> toRemove = CollectionsDelta.notInSecond(wasAffected, becameAffected);
    final Set<Pair<String, VcsKey>> toAdd = CollectionsDelta.notInSecond(becameAffected, wasAffected);

    if (toRemove != null) {
      for (Pair<String, VcsKey> pair : toRemove) {
        myDeltaListener.minus(convertPair(pair));
      }
    }
    sendPlus(toAdd);
    return toRemove != null || toAdd != null;
  }

  private void sendPlus(final Collection<Pair<String, VcsKey>> toAdd) {
    if (toAdd != null) {
      for (Pair<String, VcsKey> pair : toAdd) {
        myDeltaListener.plus(convertPair(pair));
      }
    }
  }

  private Pair<String, AbstractVcs> convertPair(final Pair<String, VcsKey> pair) {
    final VcsKey vcsKey = pair.getSecond();
    return new Pair<String, AbstractVcs>(pair.getFirst(), (vcsKey == null) ? null : myVcsManager.findVcsByName(vcsKey.getName()));
  }
}
