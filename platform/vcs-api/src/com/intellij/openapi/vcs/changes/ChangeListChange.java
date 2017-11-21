// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.NotNull;

public class ChangeListChange extends Change {
  @NotNull private final Change myChange;
  @NotNull private final String myChangeListName;
  private String myChangeListId;

  public ChangeListChange(@NotNull Change change,
                          @NotNull String changeListName,
                          @NotNull String changeListId) {
    super(change);
    myChange = change;
    myChangeListName = changeListName;
    myChangeListId = changeListId;
  }

  @NotNull
  public Change getChange() {
    return myChange;
  }

  @NotNull
  public String getChangeListName() {
    return myChangeListName;
  }

  @NotNull
  public String getChangeListId() {
    return myChangeListId;
  }
}
