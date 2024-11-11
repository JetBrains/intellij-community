// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import org.jetbrains.annotations.NotNull;

public class IgnoredViewDialog extends SpecificFilesViewDialog.SpecificFilePathsViewDialog {
  public IgnoredViewDialog(@NotNull Project project) {
    super(project, VcsBundle.message("dialog.title.ignored.files"), ChangesListView.IGNORED_FILE_PATHS_DATA_KEY,
          () -> ChangeListManager.getInstance(project).getIgnoredFilePaths());
  }

  @Override
  protected void addCustomActions(@NotNull DefaultActionGroup group) {
    AnAction deleteAction = ActionManager.getInstance().getAction(IdeActions.ACTION_DELETE);
    deleteAction.registerCustomShortcutSet(myView, null);
    group.add(deleteAction);
    myView.installPopupHandler(new DefaultActionGroup(deleteAction));
  }
}
