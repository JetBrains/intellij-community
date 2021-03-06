// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.popup.PopupFactoryImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class WelcomePopupAction extends AnAction implements DumbAware {

  protected abstract void fillActions(DefaultActionGroup group);

  protected abstract @NlsActions.ActionText String getTextForEmpty();

  protected abstract @NlsContexts.PopupTitle String getCaption();

  /**
   * When there is only one option to choose from, this method is called to determine whether
   * the popup should still be shown or that the option should be chosen silently.
   *
   * @return true to choose single option silently
   *         false otherwise
   */
  protected abstract boolean isSilentlyChooseSingleOption();

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
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
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
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
