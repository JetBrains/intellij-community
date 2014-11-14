/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.popup.PopupFactoryImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class WelcomePopupAction extends AnAction implements DumbAware {

  protected abstract void fillActions(DefaultActionGroup group);

  protected abstract String getTextForEmpty();

  protected abstract String getCaption();

  /**
   * When there is only one option to choose from, this method is called to determine whether
   * the popup should still be shown or that the option should be chosen silently.
   *
   * @return true to choose single option silently
   *         false otherwise
   */
  protected abstract boolean isSilentlyChooseSingleOption();

  public void actionPerformed(final AnActionEvent e) {
    showPopup(e);
  }

  private void showPopup(final AnActionEvent e) {
    final DefaultActionGroup group = new DefaultActionGroup();
    fillActions(group);

    if (group.getChildrenCount() == 1 && isSilentlyChooseSingleOption()) {
      final AnAction[] children = group.getChildren(null);
      children[0].actionPerformed(e);
      return;
    }


    if (group.getChildrenCount() == 0) {
      group.add(new AnAction(getTextForEmpty()) {
        public void actionPerformed(AnActionEvent e) {
          group.setPopup(false);
        }
      } );
    }

    final DataContext context = e.getDataContext();
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(getCaption(),
                              group,
                              context,
                              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                              true);

    JComponent contextComponent = null;
    InputEvent inputEvent = e.getInputEvent();
    if (inputEvent instanceof MouseEvent) {
      if (inputEvent.getSource() instanceof JComponent) {
        contextComponent = (JComponent)inputEvent.getSource();
      }
    }

    showPopup(context, popup, contextComponent);
  }

  protected void showPopup(DataContext context, ListPopup popup, JComponent contextComponent) {
    Component focusedComponent = contextComponent != null ? contextComponent : PlatformDataKeys.CONTEXT_COMPONENT.getData(context);
    if (focusedComponent != null) {
      if (popup instanceof PopupFactoryImpl.ActionGroupPopup && focusedComponent instanceof JLabel) {
        ((PopupFactoryImpl.ActionGroupPopup)popup).showUnderneathOfLabel((JLabel)focusedComponent);
      } else {
        popup.showUnderneathOf(focusedComponent);
      }
    }
    else {
      Rectangle r;
      int x;
      int y;
      focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent((Project)null);
      r = WindowManagerEx.getInstanceEx().getScreenBounds();
      x = r.x + r.width / 2;
      y = r.y + r.height / 2;
      Point point = new Point(x, y);
      SwingUtilities.convertPointToScreen(point, focusedComponent.getParent());

      popup.showInScreenCoordinates(focusedComponent.getParent(), point);
    }
  }
}
