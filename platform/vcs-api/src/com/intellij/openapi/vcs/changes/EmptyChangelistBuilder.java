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
  public void processUnversionedFile(final VirtualFile file) {
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
  public void processIgnoredFile(final VirtualFile file) {
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
