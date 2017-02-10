/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ProjectGroupActionGroup;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public abstract class RecentProjectsWelcomeScreenActionBase extends DumbAwareAction {
  @Nullable
  public static DefaultListModel getDataModel(AnActionEvent e) {
    final JList list = getList(e);
    if (list != null) {
      ListModel model = list.getModel();
      if (model instanceof NameFilteringListModel) {
        model = ((NameFilteringListModel)model).getOriginalModel();
        if (model instanceof DefaultListModel) {
          return (DefaultListModel)model;
        }
      }
    }
    return null;
  }

  @NotNull
  public static List<AnAction> getSelectedElements(AnActionEvent e) {
    final JList list = getList(e);
    final List<AnAction> actions = new ArrayList<>();
    if (list != null) {
      for (Object value : list.getSelectedValues()) {
        if (value instanceof AnAction) {
          actions.add((AnAction)value);
        }
      }
    }
    return actions;
  }

  @Nullable
  public static JList getList(AnActionEvent e) {
    final Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    if (component instanceof JList) {
      return (JList)component;
    }
    return null;
  }

  public static boolean hasGroupSelected(AnActionEvent e) {
    for (AnAction action : getSelectedElements(e)) {
      if (action instanceof ProjectGroupActionGroup) {
        return true;
      }
    }
    return false;
  }

  public static void rebuildRecentProjectsList(AnActionEvent e) {
    final DefaultListModel model = getDataModel(e);
    if (model != null) {
      rebuildRecentProjectDataModel(model);
    }
  }

  public static void rebuildRecentProjectDataModel(@NotNull DefaultListModel model) {
    model.clear();
    for (AnAction action : RecentProjectsManager.getInstance().getRecentProjectsActions(false, FlatWelcomeFrame.isUseProjectGroups())) {
      //noinspection unchecked
      model.addElement(action);
    }
  }
}
