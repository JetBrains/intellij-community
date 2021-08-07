// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

public class ChangeListChangesSupplier implements Supplier<Iterable<Change>> {
  private final @NotNull List<ChangeList> myChangeLists;

  public ChangeListChangesSupplier(@NotNull List<ChangeList> changeLists) {
    myChangeLists = ContainerUtil.newUnmodifiableList(changeLists);
  }

  public ChangeListChangesSupplier(@NotNull ChangeList changeList) {
    this(ContainerUtil.immutableSingletonList(changeList));
  }

  @Override
  public Iterable<Change> get() {
    return JBIterable.from(myChangeLists).flatMap(ChangeList::getChanges);
  }
}
