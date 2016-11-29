/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author yole
 */
public class MockChangelistBuilder implements ChangelistBuilder {
  private final List<Change> myChanges = new ArrayList<>();
  private final List<VirtualFile> myUnversionedFiles = new ArrayList<>();
  private final List<FilePath> myLocallyDeletedFiles = new ArrayList<>();
  private final List<VirtualFile> myHijackedFiles = new ArrayList<>();
  private final List<VirtualFile> myIgnoredFiles = new ArrayList<>();

  @Override
  public void processChange(Change change, VcsKey vcsKey) {
    myChanges.add(change);
  }

  @Override
  public void processChangeInList(Change change, @Nullable ChangeList changeList, VcsKey vcsKey) {
    myChanges.add(change);
  }

  @Override
  public void processChangeInList(Change change, String changeListName, VcsKey vcsKey) {
    myChanges.add(change);
  }

  @Override
  public void removeRegisteredChangeFor(FilePath path) {
    for (Iterator<Change> iterator = myChanges.iterator(); iterator.hasNext(); ) {
      final Change change = iterator.next();
      if (path.equals(ChangesUtil.getFilePath(change))) {
        iterator.remove();
        return;
      }
    }
  }

  @Override
  public void processUnversionedFile(VirtualFile file) {
    myUnversionedFiles.add(file);
  }

  @Override
  public void processLocallyDeletedFile(FilePath file) {
    myLocallyDeletedFiles.add(file);
  }

  @Override
  public void processLocallyDeletedFile(LocallyDeletedChange locallyDeletedChange) {
    myLocallyDeletedFiles.add(locallyDeletedChange.getPath());
  }

  @Override
  public void processModifiedWithoutCheckout(VirtualFile file) {
    myHijackedFiles.add(file);
  }

  @Override
  public void processIgnoredFile(VirtualFile file) {
    myIgnoredFiles.add(file);
  }

  @Override
  public void processLockedFolder(final VirtualFile file) {
  }

  @Override
  public void processLogicallyLockedFolder(VirtualFile file, LogicalLock logicalLock) {
  }

  @Override
  public void processSwitchedFile(VirtualFile file, String branch, final boolean recursive) {
  }

  @Override
  public void processRootSwitch(VirtualFile file, String branch) {
  }

  @Override
  public boolean reportChangesOutsideProject() {
    return false;
  }

  @Override
  public void reportAdditionalInfo(final String text) {
  }

  @Override
  public void reportAdditionalInfo(Factory<JComponent> infoComponent) {
  }

  public void reportWarningMessage(final String message) {
  }

  public List<Change> getChanges() {
    return myChanges;
  }

  public List<VirtualFile> getUnversionedFiles() {
    return myUnversionedFiles;
  }

  public List<FilePath> getLocallyDeletedFiles() {
    return myLocallyDeletedFiles;
  }

  public List<VirtualFile> getHijackedFiles() {
    return myHijackedFiles;
  }

  public List<VirtualFile> getIgnoredFiles() {
    return myIgnoredFiles;
  }
}
