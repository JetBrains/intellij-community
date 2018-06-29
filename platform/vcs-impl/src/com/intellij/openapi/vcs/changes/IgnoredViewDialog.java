// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IgnoredViewDialog extends SpecificFilesViewDialog {
  public IgnoredViewDialog(@NotNull Project project) {
    super(project, "Ignored Files", ChangesListView.IGNORED_FILES_DATA_KEY,
          ChangeListManagerImpl.getInstanceImpl(project).getIgnoredFiles());
  }

  @Override
  protected void addCustomActions(@NotNull DefaultActionGroup group) {
    AnAction deleteAction =
      EmptyAction.registerWithShortcutSet("ChangesView.DeleteUnversioned", CommonShortcuts.getDelete(), myView);
    group.add(deleteAction);
    myView.installPopupHandler(new DefaultActionGroup(deleteAction));
  }

  @NotNull
  @Override
  protected List<VirtualFile> getFiles() {
    return ChangeListManagerImpl.getInstanceImpl(myProject).getIgnoredFiles();
  }
}
