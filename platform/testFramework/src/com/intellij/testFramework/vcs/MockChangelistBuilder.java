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

package com.intellij.testFramework.vcs;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class MockChangelistBuilder implements ChangelistBuilder {
  private final List<Change> myChanges = new ArrayList<Change>();

  public void processChange(Change change, VcsKey vcsKey) {
    myChanges.add(change);
  }

  public void processChangeInList(Change change, @Nullable ChangeList changeList, VcsKey vcsKey) {
    myChanges.add(change);
  }

  public void processChangeInList(Change change, String changeListName, VcsKey vcsKey) {
    myChanges.add(change);
  }

  public void processUnversionedFile(VirtualFile file) {
  }

  public void processLocallyDeletedFile(FilePath file) {
  }

  public void processLocallyDeletedFile(LocallyDeletedChange locallyDeletedChange) {
  }

  public void processModifiedWithoutCheckout(VirtualFile file) {
  }

  public void processIgnoredFile(VirtualFile file) {
  }

  public void processLockedFolder(final VirtualFile file) {
  }

  public void processLogicallyLockedFolder(VirtualFile file, LogicalLock logicalLock) {
  }

  public void processSwitchedFile(VirtualFile file, String branch, final boolean recursive) {
  }

  public void processRootSwitch(VirtualFile file, String branch) {
  }

  public boolean isUpdatingUnversionedFiles() {
    return true;
  }

  public boolean reportChangesOutsideProject() {
    return false;
  }

  public void reportWarningMessage(final String message) {
  }

  public List<Change> getChanges() {
    return myChanges;
  }
}
