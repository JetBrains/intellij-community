// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import org.jetbrains.annotations.NotNull;

public interface DecoratorManager {
  void install(@NotNull CommittedChangeListDecorator decorator);

  // Not used actually
  void remove(@NotNull CommittedChangeListDecorator decorator);

  void repaintTree();

  void reportLoadedLists(@NotNull CommittedChangeListsListener listener);

  void removeFilteringStrategy(@NotNull CommittedChangesFilterKey key);

  @SuppressWarnings("UnusedReturnValue")
  boolean setFilteringStrategy(@NotNull ChangeListFilteringStrategy filteringStrategy);
}
