// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UnversionedViewDialog extends SpecificFilesViewDialog {
  private static final String TOOLBAR_GROUP = "Unversioned.Files.Dialog";
  private static final String POPUP_GROUP = "Unversioned.Files.Dialog.Popup";

  public UnversionedViewDialog(@NotNull Project project) {
    super(project, "Unversioned Files", ChangesListView.UNVERSIONED_FILES_DATA_KEY,
          ChangeListManagerImpl.getInstanceImpl(project).getUnversionedFiles());
  }

  @Override
  protected void addCustomActions(@NotNull DefaultActionGroup group) {
    ActionGroup popupGroup = getUnversionedPopupGroup();
    ActionGroup toolbarGroup = getUnversionedToolbarGroup();

    ActionUtil.recursiveRegisterShortcutSet(popupGroup, myView, null);
    EmptyAction.registerWithShortcutSet("ChangesView.DeleteUnversioned", CommonShortcuts.getDelete(), myView);

    group.add(toolbarGroup);

    myView.installPopupHandler(popupGroup);
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
  @Override
  protected List<VirtualFile> getFiles() {
    return ((ChangeListManagerImpl)myChangeListManager).getUnversionedFiles();
  }
}
