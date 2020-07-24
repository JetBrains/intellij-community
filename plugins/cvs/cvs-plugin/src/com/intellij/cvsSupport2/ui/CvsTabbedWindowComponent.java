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
package com.intellij.cvsSupport2.ui;

import com.intellij.CvsBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public class CvsTabbedWindowComponent extends JPanel implements DataProvider, CvsTabbedWindow.DeactivateListener {
  private final JComponent myComponent;
  private final ContentManager myContentManager;
  private Content myContent;
  private final boolean myAddToolbar;
  private final String myHelpId;


  public CvsTabbedWindowComponent(JComponent component, boolean addDefaultToolbar,
                                  @Nullable ActionGroup toolbarActions,
                                  ContentManager contentManager, String helpId) {
    super(new BorderLayout());
    myAddToolbar = addDefaultToolbar;
    myComponent = component;
    myContentManager = contentManager;
    myHelpId = helpId;

    add(myComponent, BorderLayout.CENTER);

    if (myAddToolbar) {
      DefaultActionGroup actionGroup = new DefaultActionGroup();
      actionGroup.add(new CloseAction());
      if (toolbarActions != null) {
        actionGroup.add(toolbarActions);
      }
      actionGroup.add(new ContextHelpAction(helpId));
      add(ActionManager.getInstance().
          createActionToolbar("DefaultCvsComponentToolbar", actionGroup, false).getComponent(),
          BorderLayout.WEST);
    }
  }


  @Override
  public Object getData(@NotNull String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return myHelpId;
    }
    return null;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void setContent(Content content) {
    myContent = content;
  }

  public JComponent getShownComponent() {
    return myAddToolbar ? this : myComponent;
  }

  private class CloseAction extends AnAction implements DumbAware {
    CloseAction() {
      super(CvsBundle.message("close.tab.action.name"), "", AllIcons.Actions.Cancel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myContentManager.removeContent(myContent, true);

      deactivated();
    }
  }

  @Override
  public void deactivated() {
    if (myComponent instanceof CvsTabbedWindow.DeactivateListener) {
      ((CvsTabbedWindow.DeactivateListener) myComponent).deactivated();
    }
  }
}
