// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public abstract class InheritedMembersNodeProvider<T extends TreeElement> implements FileStructureNodeProvider<T>, ActionShortcutProvider {
  @ApiStatus.Internal public static final @NonNls String ID = "SHOW_INHERITED";

  @Override
  public @NotNull String getCheckBoxText() {
    return StructureViewBundle.message("file.structure.toggle.show.inherited");
  }

  @Override
  public Shortcut @NotNull [] getShortcut() {
    throw new IncorrectOperationException("see getActionIdForShortcut()");
  }

  @Override
  public @NotNull String getActionIdForShortcut() {
    return "FileStructurePopup";
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return new ActionPresentationData(StructureViewBundle.message("action.structureview.show.inherited"), null, AllIcons.Hierarchy.Supertypes);
  }

  @Override
  public @NotNull String getName() {
    return ID;
  }
}
