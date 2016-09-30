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
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class UnversionedViewDialog extends SpecificFilesViewDialog {

  private AnAction myDeleteActionWithCustomShortcut;

  public UnversionedViewDialog(@NotNull Project project) {
    super(project, "Unversioned Files", ChangesListView.UNVERSIONED_FILES_DATA_KEY,
          ChangeListManagerImpl.getInstanceImpl(project).getUnversionedFiles());
  }

  @Override
  protected void addCustomActions(@NotNull DefaultActionGroup group, @NotNull ActionToolbar actionToolbar) {
    List<AnAction> actions = registerUnversionedActionsShortcuts(actionToolbar.getToolbarDataContext(), myView);
    // special shortcut for deleting a file
    actions.add(myDeleteActionWithCustomShortcut =
                  EmptyAction.registerWithShortcutSet("ChangesView.DeleteUnversioned.From.Dialog", CommonShortcuts.getDelete(), myView));

    refreshViewAfterActionPerformed(actions);
    group.add(getUnversionedActionGroup());
    final DefaultActionGroup secondGroup = new DefaultActionGroup();
    secondGroup.addAll(getUnversionedActionGroup());

    myView.setMenuActions(secondGroup);
  }

  private void refreshViewAfterActionPerformed(@NotNull final List<AnAction> actions) {
    ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (actions.contains(action)) {
          refreshView();
          if (myDeleteActionWithCustomShortcut.equals(action)) {
            // We can not utilize passed "dataContext" here as it results in
            // "cannot share data context between Swing events" assertion.
            refreshChanges(myProject, getBrowserBase(myView));
          }
        }
      }
    }, myDisposable);
  }

  @NotNull
  public static ActionGroup getUnversionedActionGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction("Unversioned.Files.Dialog");
  }

  @NotNull
  public static List<AnAction> registerUnversionedActionsShortcuts(@NotNull DataContext dataContext, @NotNull JComponent component) {
    ActionManager manager = ActionManager.getInstance();
    List<AnAction> actions = ContainerUtil.newArrayList();

    Utils.expandActionGroup(getUnversionedActionGroup(), actions, new PresentationFactory(), dataContext, "", manager);
    for (AnAction action : actions) {
      action.registerCustomShortcutSet(action.getShortcutSet(), component);
    }

    return actions;
  }

  @NotNull
  @Override
  protected List<VirtualFile> getFiles() {
    return ((ChangeListManagerImpl)myChangeListManager).getUnversionedFiles();
  }
}
