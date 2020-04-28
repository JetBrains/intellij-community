// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.changes;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.WeighedItem;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.psi.search.scope.packageSet.FilteredNamedScope;
import com.intellij.ui.OffsetIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ChangeListScope extends FilteredNamedScope implements WeighedItem {
  private static final Icon ICON = new OffsetIcon(AllIcons.Scope.ChangedFiles);
  static final String ALL_CHANGED_FILES_SCOPE_NAME = "All Changed Files";

  public ChangeListScope(@NotNull ChangeListManager manager) {
    super(ALL_CHANGED_FILES_SCOPE_NAME, AllIcons.Scope.ChangedFilesAll, 0, manager::isFileAffected);
  }

  public ChangeListScope(@NotNull ChangeListManager manager, @NotNull String name) {
    super(name, ICON, 0, file -> manager.getChangeLists(file).stream().anyMatch(list -> list.getName().equals(name)));
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) return true;
    if (object instanceof ChangeListScope) {
      ChangeListScope scope = (ChangeListScope)object;
      return scope.getIcon() == getIcon() && scope.getName().equals(getName());
    }
    return false;
  }

  @Override
  public String toString() {
    String string = super.toString();
    if (AllIcons.Scope.ChangedFilesAll == getIcon()) string += "; ALL";
    return string;
  }

  @Override
  public int getWeight() {
    return AllIcons.Scope.ChangedFilesAll == getIcon() ? 0 : 1;
  }

  public static String getNameText() {
    return "All Changed Files";
  }
}
