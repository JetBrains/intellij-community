// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ProjectGroup;
import com.intellij.ide.ProjectGroupActionGroup;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ScrollingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class EditProjectGroupAction extends RecentProjectsWelcomeScreenActionBase {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ProjectGroup group = ((ProjectGroupActionGroup)getSelectedElements(e).get(0)).getGroup();
    JList list = getList(e);
    assert list != null;
    DefaultListModel model = getDataModel(e);
    String name = Messages.showInputDialog(list, IdeBundle.message("label.enter.group.name"),
                                           IdeBundle.message("dialog.title.change.group.name"), null, group.getName(),
                                           new InputValidatorEx() {
                                             @Nullable
                                             @Override
                                             public String getErrorText(String inputString) {
                                               inputString = inputString.trim();
                                               if (inputString.length() == 0) {
                                                 return IdeBundle.message("error.name.cannot.be.empty");
                                               }
                                               if (!checkInput(inputString)) {
                                                 return IdeBundle.message("error.group.already.exists", inputString);
                                               }
                                               return null;
                                             }

                                             @Override
                                             public boolean checkInput(String inputString) {
                                               inputString = inputString.trim();
                                               if (inputString.equals(group.getName())) return true;
                                               for (ProjectGroup projectGroup : RecentProjectsManager.getInstance().getGroups()) {
                                                 if (projectGroup.getName().equals(inputString)) {
                                                   return false;
                                                 }
                                               }
                                               return true;
                                             }

                                             @Override
                                             public boolean canClose(String inputString) {
                                               return true;
                                             }
                                           });
    if (name != null && model != null) {
      group.setName(name);
      rebuildRecentProjectDataModel(model);
      for (int i = 0; i < model.getSize(); i++) {
        Object element = model.get(i);
        if (element instanceof ProjectGroupActionGroup) {
           if (((ProjectGroupActionGroup)element).getGroup().equals(group)) {
             ScrollingUtil.selectItem(list, i);
             break;
           }
         }
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final List<AnAction> selected = getSelectedElements(e);
    boolean enabled = !selected.isEmpty() && selected.get(0) instanceof ProjectGroupActionGroup && !((ProjectGroupActionGroup)selected.get(0)).getGroup().isTutorials();
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
