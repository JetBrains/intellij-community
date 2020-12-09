// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ProjectGroupActionGroup;
import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.lightEdit.LightEditCompatible;
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
public abstract class RecentProjectsWelcomeScreenActionBase extends DumbAwareAction implements LightEditCompatible {
  @Nullable
  public static DefaultListModel getDataModel(@NotNull AnActionEvent e) {
    JList list = getList(e);
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
  public static List<AnAction> getSelectedElements(@NotNull AnActionEvent e) {
    JList list = getList(e);
    List<AnAction> actions = new ArrayList<>();
    if (list != null) {
      for (Object value : list.getSelectedValuesList()) {
        if (value instanceof AnAction) {
          actions.add((AnAction)value);
        }
      }
    }
    return actions;
  }

  @Nullable
  public static JList getList(@NotNull AnActionEvent e) {
    Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    if (component instanceof JList) {
      return (JList)component;
    }
    return null;
  }

  public static boolean hasGroupSelected(@NotNull AnActionEvent e) {
    for (AnAction action : getSelectedElements(e)) {
      if (action instanceof ProjectGroupActionGroup) {
        return true;
      }
    }
    return false;
  }

  public static void rebuildRecentProjectsList(@NotNull AnActionEvent e) {
    DefaultListModel model = getDataModel(e);
    if (model != null) {
      rebuildRecentProjectDataModel(model);
    }
  }

  public static void rebuildRecentProjectDataModel(@NotNull DefaultListModel model) {
    model.clear();
    for (AnAction action : RecentProjectListActionProvider.getInstance().getActions(false, true)) {
      //noinspection unchecked
      model.addElement(action);
    }
  }
}
