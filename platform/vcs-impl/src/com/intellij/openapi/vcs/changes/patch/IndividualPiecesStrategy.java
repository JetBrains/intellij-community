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
import com.intellij.util.containers.MultiMap;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

class IndividualPiecesStrategy extends AutoMatchStrategy {
  private boolean mySucceeded;

  IndividualPiecesStrategy(VirtualFile baseDir) {
    super(baseDir);

    mySucceeded = true;
  }

  @Override
  public void acceptPatch(TextFilePatch patch, Collection<VirtualFile> foundByName) {
    if (! mySucceeded) return;

    final Collection<VirtualFile> variants = filterVariants(patch, foundByName);

    if ((variants != null) && (! variants.isEmpty())) {
      final FilePatchInProgress filePatchInProgress = new FilePatchInProgress(patch, variants, myBaseDir);
      myResult.add(filePatchInProgress);
      registerFolderDecision(patch.getBeforeName(), filePatchInProgress.getBase());
    } else {
      mySucceeded = false;
    }
  }

  @Override
  public void processCreation(TextFilePatch creation) {
    if (! mySucceeded) return;

    processCreationBasedOnFolderDecisions(creation);
  }

  @Override
  public boolean succeeded() {
    return mySucceeded;
  }

  @Override
  public void beforeCreations() {
  }
}
