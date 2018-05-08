/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    myView.setMenuActions(new DefaultActionGroup(deleteAction));
  }

  @NotNull
  @Override
  protected List<VirtualFile> getFiles() {
    return ChangeListManagerImpl.getInstanceImpl(myProject).getIgnoredFiles();
  }
}
