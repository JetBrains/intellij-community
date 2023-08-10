// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.testFramework.vcs;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class MockChangelistBuilder implements ChangelistBuilder {
  private final List<Change> myChanges = new ArrayList<>();
  private final List<FilePath> myUnversionedFiles = new ArrayList<>();
  private final List<FilePath> myLocallyDeletedFiles = new ArrayList<>();
  private final List<VirtualFile> myHijackedFiles = new ArrayList<>();
  private final List<FilePath> myIgnoredFiles = new ArrayList<>();

  @Override
  public void processChange(@NotNull Change change, VcsKey vcsKey) {
    myChanges.add(change);
  }

  @Override
  public void processChangeInList(@NotNull Change change, @Nullable ChangeList changeList, VcsKey vcsKey) {
    myChanges.add(change);
  }

  @Override
  public void processChangeInList(@NotNull Change change, String changeListName, VcsKey vcsKey) {
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
  public void processUnversionedFile(FilePath file) {
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
  public void processIgnoredFile(FilePath file) {
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

  public List<Change> getChanges() {
    return myChanges;
  }

  public List<FilePath> getUnversionedFiles() {
    return myUnversionedFiles;
  }

  public List<FilePath> getLocallyDeletedFiles() {
    return myLocallyDeletedFiles;
  }

  public List<VirtualFile> getHijackedFiles() {
    return myHijackedFiles;
  }

  public List<FilePath> getIgnoredFiles() {
    return myIgnoredFiles;
  }
}
