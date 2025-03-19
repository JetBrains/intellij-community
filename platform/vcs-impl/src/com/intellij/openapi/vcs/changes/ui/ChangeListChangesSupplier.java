// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class ChangeListChangesSupplier implements Supplier<Iterable<Change>> {
  private final @NotNull List<ChangeList> myChangeLists;

  public ChangeListChangesSupplier(@NotNull List<? extends ChangeList> changeLists) {
    myChangeLists = List.copyOf(changeLists);
  }

  public ChangeListChangesSupplier(@NotNull ChangeList changeList) {
    myChangeLists = List.of(changeList);
  }

  @Override
  public Iterable<Change> get() {
    return JBIterable.from(myChangeLists).flatMap(ChangeList::getChanges);
  }
}
