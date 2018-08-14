// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AlienLocalChangeList extends LocalChangeList {
  private final List<Change> myChanges;
  private String myName;
  private String myComment;

  public AlienLocalChangeList(@NotNull List<Change> changes, @NotNull String name) {
    myChanges = changes;
    myName = name;
    myComment = "";
  }

  @Override
  public Collection<Change> getChanges() {
    return myChanges;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull final String name) {
    myName = name;
  }

  @Override
  public String getComment() {
    return myComment;
  }

  @Override
  public void setComment(final String comment) {
    myComment = comment;
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public void setReadOnly(final boolean isReadOnly) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public Object getData() {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalChangeList copy() {
    throw new UnsupportedOperationException();
  }

  public static final AlienLocalChangeList DEFAULT_ALIEN = new AlienLocalChangeList(Collections.emptyList(), "Default") {
    @Override
    public boolean isDefault() {
      return true;
    }
  };
}
