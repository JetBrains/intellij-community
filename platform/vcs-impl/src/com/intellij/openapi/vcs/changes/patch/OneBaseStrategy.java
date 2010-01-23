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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class OneBaseStrategy extends AutoMatchStrategy {
  private boolean mySucceeded;
  private MultiMap<VirtualFile, FilePatchInProgress> myVariants;
  private boolean myCheckExistingVariants;

  OneBaseStrategy(VirtualFile baseDir) {
    super(baseDir);
    mySucceeded = true;
    myVariants = new MultiMap<VirtualFile, FilePatchInProgress>();
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
    final List<FilePatchInProgress> results = new LinkedList<FilePatchInProgress>();
    final Set<VirtualFile> keysToRemove = new HashSet<VirtualFile>(myVariants.keySet());
    for (VirtualFile file : foundByName) {
      final FilePatchInProgress filePatchInProgress = processMatch(patch, file);
      if (filePatchInProgress != null) {
        final VirtualFile base = filePatchInProgress.getBase();
        if (myCheckExistingVariants && (! myVariants.containsKey(base))) continue;
        keysToRemove.remove(base);
        results.add(filePatchInProgress);
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
    for (FilePatchInProgress filePatchInProgress : results) {
      filePatchInProgress.setAutoBases(exactMatch);
      myVariants.putValue(filePatchInProgress.getBase(), filePatchInProgress);
    }
    myCheckExistingVariants = true;
  }

  @Override
  public void processCreation(TextFilePatch creation) {
    if (! mySucceeded) return;
    final FilePatchInProgress filePatchInProgress;
    if (myVariants.isEmpty()) {
      filePatchInProgress = new FilePatchInProgress(creation, null, myBaseDir);
    } else {
      filePatchInProgress = new FilePatchInProgress(creation, null, myVariants.keySet().iterator().next());
    }
    myResult.add(filePatchInProgress);
  }

  @Override
  public boolean succeeded() {
    return mySucceeded;
  }

  @Override
  public void beforeCreations() {
    if (! mySucceeded) return;
    if (myVariants.size() > 1) {
      Pair<VirtualFile, Collection<FilePatchInProgress>> privilegedSurvivor = null;
      for (VirtualFile file : myVariants.keySet()) {
        final Collection<FilePatchInProgress> patches = myVariants.get(file);
        int numStrip = -1;
        boolean sameStrip = true;
        for (FilePatchInProgress patch : patches) {
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
          privilegedSurvivor = new Pair<VirtualFile, Collection<FilePatchInProgress>>(file, patches);
          break;
        }
      }
      if (privilegedSurvivor == null) {
        final VirtualFile first = myVariants.keySet().iterator().next();
        privilegedSurvivor = new Pair<VirtualFile, Collection<FilePatchInProgress>>(first, myVariants.get(first));
      }
      myVariants.clear();
      myVariants.put(privilegedSurvivor.getFirst(), privilegedSurvivor.getSecond());
    }
    myResult.addAll(myVariants.values());
  }
}
