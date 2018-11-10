// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.errorView.impl;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.errorView.ContentManagerProvider;
import com.intellij.ui.errorView.ErrorViewFactory;
import com.intellij.util.ui.ErrorTreeView;

import javax.swing.*;

public class ErrorViewFactoryImpl implements ErrorViewFactory {
  @Override
  public ErrorTreeView createErrorTreeView(Project project,
                                           String helpId,
                                           boolean createExitAction,
                                           final AnAction[] extraPopupMenuActions,
                                           final AnAction[] extraRightToolbarGroupActions,
                                           final ContentManagerProvider contentManagerProvider) {
    return new NewErrorTreeViewPanel(project, helpId, createExitAction) {
      @Override
      protected void addExtraPopupMenuActions(DefaultActionGroup group) {
        super.addExtraPopupMenuActions(group);
        if (extraPopupMenuActions != null){
          for (AnAction extraPopupMenuAction : extraPopupMenuActions) {
            group.add(extraPopupMenuAction);
          }
        }
      }

      @Override
      protected void fillRightToolbarGroup(DefaultActionGroup group) {
        super.fillRightToolbarGroup(group);
        if (extraRightToolbarGroupActions != null){
          for (AnAction extraRightToolbarGroupAction : extraRightToolbarGroupActions) {
            group.add(extraRightToolbarGroupAction);
          }
        }
      }

      @Override
      public void close() {
        removeFromContentManager(contentManagerProvider.getParentContent(), this);
      }
    };
  }

  public static void removeFromContentManager(ContentManager contentManager, ErrorTreeView view) {
    if (view instanceof JComponent) {
      Content content = contentManager.getContent((JComponent)view);
      if (content != null) {
        contentManager.removeContent(content, true);
      }
    }
  }
}
