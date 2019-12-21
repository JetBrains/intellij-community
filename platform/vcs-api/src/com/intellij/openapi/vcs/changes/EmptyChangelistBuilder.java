// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class EmptyChangelistBuilder implements ChangelistBuilder {
  @Override
  public void processChange(final Change change, VcsKey vcsKey) {
  }

  @Override
  public void processChangeInList(final Change change, @Nullable final ChangeList changeList, VcsKey vcsKey) {
  }

  @Override
  public void processChangeInList(final Change change, final String changeListName, VcsKey vcsKey) {
  }

  @Override
  public void removeRegisteredChangeFor(FilePath path) {
  }

  @Override
  public void processUnversionedFile(FilePath file) {
  }

  @Override
  public void processLocallyDeletedFile(final FilePath file) {
  }

  @Override
  public void processLocallyDeletedFile(LocallyDeletedChange locallyDeletedChange) {
  }

  @Override
  public void processModifiedWithoutCheckout(final VirtualFile file) {
  }

  @Override
  public void processIgnoredFile(FilePath file) {
  }

  @Override
  public void processLockedFolder(final VirtualFile file) {
  }

  @Override
  public void processLogicallyLockedFolder(VirtualFile file, LogicalLock logicalLock) {
  }

  @Override
  public void processSwitchedFile(final VirtualFile file, final String branch, final boolean recursive) {
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
}
