/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;

import java.util.*;

class OneBaseStrategy extends AutoMatchStrategy {
  private boolean mySucceeded;
  private final MultiMap<VirtualFile, TextFilePatchInProgress> myVariants;
  private boolean myCheckExistingVariants;

  OneBaseStrategy(VirtualFile baseDir) {
    super(baseDir);
    mySucceeded = true;
    myVariants = new MultiMap<>();
    myCheckExistingVariants = false;
  }

  @Override
  public void acceptPatch(TextFilePatch patch, Collection<VirtualFile> foundByName) {
    if (! mySucceeded) return;
    // a short check
    if (foundByName.isEmpty()) {
      mySucceeded = false;
      return;
    }
    final List<TextFilePatchInProgress> results = new LinkedList<>();
    final Set<VirtualFile> keysToRemove = new HashSet<>(myVariants.keySet());
    for (VirtualFile file : foundByName) {
      final TextFilePatchInProgress textFilePatchInProgress = processMatch(patch, file);
      if (textFilePatchInProgress != null) {
        final VirtualFile base = textFilePatchInProgress.getBase();
        if (myCheckExistingVariants && (! myVariants.containsKey(base))) continue;
        keysToRemove.remove(base);
        results.add(textFilePatchInProgress);
      }
    }
    if (myCheckExistingVariants) {
      for (VirtualFile file : keysToRemove) {
        myVariants.remove(file);
      }
      if (myVariants.isEmpty()) {
        mySucceeded = false;
        return;
      }
    }
    final Collection<VirtualFile> exactMatch = filterVariants(patch, foundByName);
    for (TextFilePatchInProgress textFilePatchInProgress : results) {
      textFilePatchInProgress.setAutoBases(exactMatch);
      myVariants.putValue(textFilePatchInProgress.getBase(), textFilePatchInProgress);
    }
    myCheckExistingVariants = true;
  }

  @Override
  public void processCreation(TextFilePatch creation) {
    if (! mySucceeded) return;
    final TextFilePatchInProgress textFilePatchInProgress;
    if (myVariants.isEmpty()) {
      textFilePatchInProgress = new TextFilePatchInProgress(creation, null, myBaseDir);
    } else {
      textFilePatchInProgress = new TextFilePatchInProgress(creation, null, myVariants.keySet().iterator().next());
    }
    myResult.add(textFilePatchInProgress);
  }

  @Override
  public boolean succeeded() {
    return mySucceeded;
  }

  @Override
  public void beforeCreations() {
    if (! mySucceeded) return;
    if (myVariants.size() > 1) {
      Pair<VirtualFile, Collection<TextFilePatchInProgress>> privilegedSurvivor = null;
      for (VirtualFile file : myVariants.keySet()) {
        final Collection<TextFilePatchInProgress> patches = myVariants.get(file);
        int numStrip = -1;
        boolean sameStrip = true;
        for (TextFilePatchInProgress patch : patches) {
          if (numStrip == -1) {
            numStrip = patch.getCurrentStrip();
          } else {
            if (numStrip != patch.getCurrentStrip()) {
              sameStrip = false;
              break;
            }
          }
        }
        if (sameStrip) {
          privilegedSurvivor = Pair.create(file, patches);
          break;
        }
      }
      if (privilegedSurvivor == null) {
        final VirtualFile first = myVariants.keySet().iterator().next();
        privilegedSurvivor = Pair.create(first, myVariants.get(first));
      }
      myVariants.clear();
      myVariants.put(privilegedSurvivor.getFirst(), privilegedSurvivor.getSecond());
    }
    myResult.addAll(myVariants.values());
  }
}
