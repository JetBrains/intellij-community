// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;

import java.util.Comparator;

class ChangelistComparator implements Comparator<ShelvedChangeList> {
  private static final ChangelistComparator ourInstance = new ChangelistComparator();

  public static ChangelistComparator getInstance() {
    return ourInstance;
  }

  @Override
  public int compare(ShelvedChangeList o1, ShelvedChangeList o2) {
    return o2.getDate().compareTo(o1.getDate());
  }
}
