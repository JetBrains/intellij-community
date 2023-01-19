// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public class ChangeListAdapter implements ChangeListListener {
  public void changeListsChanged() {
  }


  @Override
  public void changeListAdded(ChangeList list) {
    changeListsChanged();
  }

  @Override
  public void changeListRemoved(ChangeList list) {
    changeListsChanged();
  }

  @Override
  public void changeListRenamed(ChangeList list, @NlsSafe String oldName) {
    changeListsChanged();
  }

  @Override
  public void changeListDataChanged(@NotNull ChangeList list) {
    changeListsChanged();
  }

  @Override
  public void changeListCommentChanged(ChangeList list, @NlsSafe String oldComment) {
    changeListsChanged();
  }

  @Override
  public void changeListChanged(ChangeList list) {
    changeListsChanged();
  }

  @Override
  public void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList) {
    changeListsChanged();
  }


  @Override
  public void changesAdded(Collection<? extends Change> changes, ChangeList toList) {
    changeListsChanged();
  }

  @Override
  public void changesRemoved(Collection<? extends Change> changes, ChangeList fromList) {
    changeListsChanged();
  }

  @Override
  public void changesMoved(Collection<? extends Change> changes, ChangeList fromList, ChangeList toList) {
    changeListsChanged();
  }

  @Override
  public void allChangeListsMappingsChanged() {
    changeListsChanged();
  }
}