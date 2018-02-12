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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerGate;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class MockChangeListManagerGate implements ChangeListManagerGate {
  private final ChangeListManager myManager;

  public MockChangeListManagerGate(final ChangeListManager manager) {
    myManager = manager;
  }

  @NotNull
  @Override
  public List<LocalChangeList> getListsCopy() {
    return myManager.getChangeListsCopy();
  }

  @Override
  public LocalChangeList findChangeList(final String name) {
    return myManager.findChangeList(name);
  }

  @NotNull
  @Override
  public LocalChangeList addChangeList(@NotNull final String name, final String comment) {
    return myManager.addChangeList(name, comment);
  }

  @NotNull
  @Override
  public LocalChangeList findOrCreateList(@NotNull final String name, final String comment) {
    LocalChangeList changeList = myManager.findChangeList(name);
    if (changeList == null) {
      changeList = myManager.addChangeList(name, comment);
    }
    return changeList;
  }

  @Override
  public void editComment(@NotNull final String name, final String comment) {
    myManager.editComment(name, comment);
  }

  @Override
  public void editName(@NotNull String oldName, @NotNull String newName) {
    myManager.editName(oldName, newName);
  }

  @Override
  public void setListsToDisappear(@NotNull Collection<String> names) { }

  @Override
  public FileStatus getStatus(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public FileStatus getStatus(@NotNull File file) {
    return null;
  }

  @Override
  public FileStatus getStatus(@NotNull FilePath filePath) {
    return null;
  }

  @Override
  public void setDefaultChangeList(@NotNull String list) {
  }
}
