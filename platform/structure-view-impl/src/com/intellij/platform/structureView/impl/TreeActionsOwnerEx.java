// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl;

import com.intellij.ide.structureView.newStructureView.TreeActionsOwner;
import com.intellij.ide.util.treeView.smartTree.TreeAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface TreeActionsOwnerEx extends TreeActionsOwner {
  void setActionActive(@NotNull TreeAction action, boolean state);

  boolean isActionActive(@NotNull TreeAction action);
}
