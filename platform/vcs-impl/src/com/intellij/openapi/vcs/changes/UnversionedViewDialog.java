// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.diff.util.DiffUtil.recursiveRegisterShortcutSet;

public final class UnversionedViewDialog extends SpecificFilesViewDialog.SpecificFilePathsViewDialog {
  private static final String TOOLBAR_GROUP = "Unversioned.Files.Dialog";
  private static final String POPUP_GROUP = "Unversioned.Files.Dialog.Popup";

  public UnversionedViewDialog(@NotNull Project project) {
    super(project, VcsBundle.message("dialog.title.unversioned.files"), ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY,
          () -> ChangeListManager.getInstance(project).getUnversionedFilesPaths());
  }

  @Override
  protected void addCustomActions(@NotNull DefaultActionGroup group) {
    group.add(getUnversionedToolbarGroup());
    myView.installPopupHandler(registerUnversionedPopupGroup(myView));
  }

  public static @NotNull ActionGroup getUnversionedToolbarGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction(TOOLBAR_GROUP);
  }

  public static @NotNull ActionGroup getUnversionedPopupGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction(POPUP_GROUP);
  }

  public static @NotNull ActionGroup registerUnversionedPopupGroup(@NotNull JComponent component) {
    ActionGroup popupGroup = getUnversionedPopupGroup();
    recursiveRegisterShortcutSet(popupGroup, component, null);
    return popupGroup;
  }
}
