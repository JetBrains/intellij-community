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
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

public class DefaultPatchStrategy extends AutoMatchStrategy {
  public DefaultPatchStrategy(final VirtualFile baseDir) {
    super(baseDir);
  }

  @Override
  public void acceptPatch(TextFilePatch patch, Collection<VirtualFile> foundByName) {
    TextFilePatchInProgress longest = null;
    for (VirtualFile file : foundByName) {
      final TextFilePatchInProgress current = processMatch(patch, file);
      if ((current != null) && ((longest == null) || (longest.getCurrentStrip() > current.getCurrentStrip()))) {
        longest = current;
      }
    }
    if (longest != null) {
      registerFolderDecision(longest.getPatch().getBeforeName(), longest.getBase());
      myResult.add(longest);
    } else {
      myResult.add(new TextFilePatchInProgress(patch, null, myBaseDir));
    }
  }

  @Override
  public void processCreation(TextFilePatch creation) {
    processCreationBasedOnFolderDecisions(creation);
  }

  @Override
  public void beforeCreations() {
  }

  @Override
  public boolean succeeded() {
    return true;
  }
}
