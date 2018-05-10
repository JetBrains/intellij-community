/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class UnversionedViewDialog extends SpecificFilesViewDialog {

  public UnversionedViewDialog(@NotNull Project project) {
    super(project, "Unversioned Files", ChangesListView.UNVERSIONED_FILES_DATA_KEY,
          ChangeListManagerImpl.getInstanceImpl(project).getUnversionedFiles());
  }

  @Override
  protected void addCustomActions(@NotNull DefaultActionGroup group) {
    registerUnversionedActionsShortcuts(myView);

    EmptyAction.registerWithShortcutSet("ChangesView.DeleteUnversioned", CommonShortcuts.getDelete(), myView);

    group.add(getUnversionedActionGroup());
    final DefaultActionGroup secondGroup = new DefaultActionGroup();
    secondGroup.addAll(getUnversionedActionGroup());

    myView.setMenuActions(secondGroup);
  }

  @NotNull
  public static ActionGroup getUnversionedActionGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction("Unversioned.Files.Dialog");
  }

  public static void registerUnversionedActionsShortcuts(@NotNull JComponent component) {
    ActionUtil.recursiveRegisterShortcutSet(getUnversionedActionGroup(), component, null);
  }

  @NotNull
  @Override
  protected List<VirtualFile> getFiles() {
    return ((ChangeListManagerImpl)myChangeListManager).getUnversionedFiles();
  }
}
