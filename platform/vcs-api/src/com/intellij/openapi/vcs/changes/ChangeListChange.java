// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

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


  public static final TObjectHashingStrategy<Object> HASHING_STRATEGY = new TObjectHashingStrategy<Object>() {
    @Override
    public int computeHashCode(Object object) {
      return Objects.hashCode(object);
    }

    @Override
    public boolean equals(Object o1, Object o2) {
      if (o1 instanceof Change && o2 instanceof Change) {
        if (o1 instanceof ChangeListChange || o2 instanceof ChangeListChange) {
          if (o1 instanceof ChangeListChange && o2 instanceof ChangeListChange) {
            ChangeListChange lc1 = (ChangeListChange)o1;
            ChangeListChange lc2 = (ChangeListChange)o2;
            return Objects.equals(o1, o2) &&
                   Objects.equals(lc1.getChangeListId(), lc2.getChangeListId());
          }
          return false;
        }
      }
      return Objects.equals(o1, o2);
    }
  };
}
