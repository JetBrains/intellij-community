// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.intellij.openapi.actionSystem.ex.ActionUtil.recursiveRegisterShortcutSet;

public class UnversionedViewDialog extends SpecificFilesViewDialog {
  private static final String TOOLBAR_GROUP = "Unversioned.Files.Dialog";
  private static final String POPUP_GROUP = "Unversioned.Files.Dialog.Popup";

  public UnversionedViewDialog(@NotNull Project project) {
    this(project, ChangeListManagerImpl.getInstanceImpl(project).getUnversionedFilesPaths());
  }

  public UnversionedViewDialog(@NotNull Project project, @NotNull List<FilePath> filePaths) {
    super(project, VcsBundle.message("dialog.title.unversioned.files"), ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY, filePaths);
  }

  @Override
  protected void addCustomActions(@NotNull DefaultActionGroup group) {
    group.add(getUnversionedToolbarGroup());
    myView.installPopupHandler(registerUnversionedPopupGroup(myView));
  }

  @NotNull
  public static ActionGroup getUnversionedToolbarGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction(TOOLBAR_GROUP);
  }

  @NotNull
  public static ActionGroup getUnversionedPopupGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction(POPUP_GROUP);
  }

  @NotNull
  public static ActionGroup registerUnversionedPopupGroup(@NotNull JComponent component) {
    ActionGroup popupGroup = getUnversionedPopupGroup();
    recursiveRegisterShortcutSet(popupGroup, component, null);
    return popupGroup;
  }

  @NotNull
  @Override
  protected List<FilePath> getFiles() {
    return ((ChangeListManagerImpl)myChangeListManager).getUnversionedFilesPaths();
  }
}
