// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IgnoredViewDialog extends SpecificFilesViewDialog {
  public IgnoredViewDialog(@NotNull Project project) {
    super(project, VcsBundle.message("dialog.title.ignored.files"), ChangesListView.IGNORED_FILE_PATHS_DATA_KEY,
          ChangeListManager.getInstance(project).getIgnoredFilePaths());
  }

  @Override
  protected void addCustomActions(@NotNull DefaultActionGroup group) {
    AnAction deleteAction = ActionManager.getInstance().getAction(IdeActions.ACTION_DELETE);
    deleteAction.registerCustomShortcutSet(myView, null);
    group.add(deleteAction);
    myView.installPopupHandler(new DefaultActionGroup(deleteAction));
  }

  @NotNull
  @Override
  protected List<FilePath> getFiles() {
    return ChangeListManager.getInstance(myProject).getIgnoredFilePaths();
  }
}
