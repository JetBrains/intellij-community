// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;


@ApiStatus.Internal
public class ReceivedChangeList extends CommittedChangeListImpl {
  @NotNull private final CommittedChangeList myBaseList;
  private final int myBaseCount;
  private boolean myForcePartial;

  public ReceivedChangeList(@NotNull CommittedChangeList baseList) {
    super(baseList.getName(), baseList.getComment(), baseList.getCommitterName(),
          baseList.getNumber(), baseList.getCommitDate(), Collections.emptyList());
    myBaseList = baseList;
    myBaseCount = baseList.getChanges().size();
    myForcePartial = false;
  }

  public void addChange(Change change) {
    myChanges.add(change);
  }

  public boolean isPartial() {
    return myForcePartial || myChanges.size() < myBaseCount;
  }

  public void setForcePartial(final boolean forcePartial) {
    myForcePartial = forcePartial;
  }

  @Override
  public AbstractVcs getVcs() {
    return myBaseList.getVcs();
  }

  @NotNull
  public CommittedChangeList getBaseList() {
    return myBaseList;
  }

  @Override
  public void setDescription(String newMessage) {
    myBaseList.setDescription(newMessage);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ReceivedChangeList that = (ReceivedChangeList)o;

    if (!myBaseList.equals(that.myBaseList)) return false;

    return true;
  }

  public int hashCode() {
    return myBaseList.hashCode();
  }

  static CommittedChangeList unwrap(CommittedChangeList changeList) {
    if (changeList instanceof ReceivedChangeList) {
      changeList = ((ReceivedChangeList) changeList).getBaseList();
    }
    return changeList;
  }

  @Override
  public String toString() {
    return myBaseList.toString();
  }
}
