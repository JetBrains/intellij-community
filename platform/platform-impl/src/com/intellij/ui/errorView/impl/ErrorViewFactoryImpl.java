/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui.errorView.impl;

import com.intellij.ui.errorView.ErrorViewFactory;
import com.intellij.ui.errorView.ContentManagerProvider;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;

import javax.swing.*;

public class ErrorViewFactoryImpl implements ErrorViewFactory {
  public ErrorTreeView createErrorTreeView(Project project,
                                           String helpId,
                                           boolean createExitAction,
                                           final AnAction[] extraPopupMenuActions,
                                           final AnAction[] extraRightToolbarGroupActions,
                                           final ContentManagerProvider contentManagerProvider) {
    return new NewErrorTreeViewPanel(project, helpId, createExitAction) {
      protected void addExtraPopupMenuActions(DefaultActionGroup group) {
        super.addExtraPopupMenuActions(group);
        if (extraPopupMenuActions != null){
          for (AnAction extraPopupMenuAction : extraPopupMenuActions) {
            group.add(extraPopupMenuAction);
          }
        }
      }

      protected void fillRightToolbarGroup(DefaultActionGroup group) {
        super.fillRightToolbarGroup(group);
        if (extraRightToolbarGroupActions != null){
          for (AnAction extraRightToolbarGroupAction : extraRightToolbarGroupActions) {
            group.add(extraRightToolbarGroupAction);
          }
        }
      }

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
