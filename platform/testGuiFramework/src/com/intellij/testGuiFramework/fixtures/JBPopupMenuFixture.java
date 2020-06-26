// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.testGuiFramework.framework.Timeouts;
import com.intellij.util.ArrayUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static junit.framework.Assert.assertNotNull;
import static org.fest.swing.timing.Pause.pause;

public final class JBPopupMenuFixture extends JComponentFixture<JBPopupMenuFixture, JBPopupMenu> {
  private final JBPopupMenu myContextMenu;
  private final Robot myRobot;

  private JBPopupMenuFixture(JBPopupMenu menu, Robot robot) {
    super(JBPopupMenuFixture.class, robot, menu);

    myContextMenu = menu;
    myRobot = robot;
  }

  public static JBPopupMenuFixture findContextMenu(Robot robot) {

    try {
      pause(new Condition("Find context menu") {
        @Override
        public boolean test() {
          final JBPopupMenu contextMenu = robot.finder().findByType(JBPopupMenu.class);
          return contextMenu != null;
        }
      }, Timeouts.INSTANCE.getMinutes02());
    } catch (WaitTimedOutError e) {
      throw new ComponentLookupException("Unable to find context menu for JBPopupFixture");
    }

    final JBPopupMenu contextMenu = robot.finder().findByType(JBPopupMenu.class);
    assertNotNull(contextMenu);

    return new JBPopupMenuFixture(contextMenu, robot);
  }

  public void assertContainsAction(String actionName) {
    boolean assertion = false;
    final MenuElement[] elements = myContextMenu.getSubElements();

    for (MenuElement element : elements) {
      if (element instanceof ActionMenuItem) {
        if (StringUtil.toLowerCase(((ActionMenuItem)element).getText()).contains(StringUtil.toLowerCase(actionName))) assertion = true;
      }
    }
    if (!assertion) System.err.println("Unable to find action \"" + actionName + "\" in popupMenu");
    assert assertion;
  }

  public void invokeAction(String... actionPath) {
    boolean assertion = false;
    final MenuElement[] elements = myContextMenu.getSubElements();
    if (actionPath.length > 1) {
      //actionGroup
      for (MenuElement element : elements) {
        if (element instanceof ActionMenu) {
          final ActionMenu actionMenu = (ActionMenu)element;
          if (StringUtil.toLowerCase(actionMenu.getText()).contains(StringUtil.toLowerCase(actionPath[0]))) {
            final Point locationOnScreen = myContextMenu.getLocationOnScreen();
            final Rectangle bounds = actionMenu.getBounds();
            final Point point =
              new Point(locationOnScreen.x + bounds.x + bounds.width / 2, locationOnScreen.y + bounds.y + bounds.height / 2);
            robot().click(point, MouseButton.LEFT_BUTTON, 1);
            //invoke action for a new JBPopupMenu
            final String actionName = actionPath[1];
            final JBPopupMenuFixture fixture = new JBPopupMenuFixture(waitUntilFoundMenu(actionName), myRobot);
            fixture.invokeAction(ArrayUtil.remove(actionPath, 0));
            return;
          }
        }
      }
    }
    else {
      //actionMenuItem
      for (MenuElement element : elements) {
        if (element instanceof ActionMenuItem) {
          final ActionMenuItem actionMenuItem = (ActionMenuItem)element;
          if (StringUtil.toLowerCase(actionMenuItem.getText()).contains(StringUtil.toLowerCase(actionPath[0]))) {
            pause(new Condition("Waiting to showing JBPopupMenu on screen") {
              @Override
              public boolean test() {
                try{
                  myContextMenu.getLocationOnScreen();
                  return true;
                } catch (IllegalComponentStateException e) {
                  return false;
                }
              }
            }, Timeouts.INSTANCE.getMinutes02());
            final Point locationOnScreen = myContextMenu.getLocationOnScreen();
            final Rectangle bounds = actionMenuItem.getBounds();
            final Point point =
              new Point(locationOnScreen.x + bounds.x + bounds.width / 2, locationOnScreen.y + bounds.y + bounds.height / 2);
            robot().click(point, MouseButton.LEFT_BUTTON, 1);
            return;
          }
        }
      }
    }
  }

  private JBPopupMenu waitUntilFoundMenu(final String actionName) {
    return GuiTestUtil.INSTANCE.waitUntilFound(robot(), new GenericTypeMatcher<JBPopupMenu>(JBPopupMenu.class) {
      @Override
      protected boolean isMatching(@NotNull JBPopupMenu menu) {
        boolean found = false;
        for (MenuElement menuElement : menu.getSubElements()) {
          if (menuElement instanceof ActionMenu) {
            if (StringUtil.toLowerCase(((ActionMenu)menuElement).getText()).equals(StringUtil.toLowerCase(actionName))) {
              found = true;
            }
          }
          else if (menuElement instanceof ActionMenuItem) {
            if (StringUtil.toLowerCase(((ActionMenuItem)menuElement).getText()).equals(StringUtil.toLowerCase(actionName))) {
              found = true;
            }
          }
        }
        return found;
      }
    });
  }
}
