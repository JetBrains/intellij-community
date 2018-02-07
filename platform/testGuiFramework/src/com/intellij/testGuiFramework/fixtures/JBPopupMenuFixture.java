/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
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

import static com.intellij.testGuiFramework.framework.GuiTestUtil.SHORT_TIMEOUT;
import static com.intellij.testGuiFramework.framework.GuiTestUtil.waitUntilFound;
import static junit.framework.Assert.assertNotNull;
import static org.fest.swing.timing.Pause.pause;

public class JBPopupMenuFixture extends JComponentFixture<JBPopupMenuFixture, JBPopupMenu> {
  private JBPopupMenu myContextMenu;
  private Robot myRobot;

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
      }, SHORT_TIMEOUT);
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
        if (((ActionMenuItem)element).getText().toLowerCase().contains(actionName.toLowerCase())) assertion = true;
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
          if (actionMenu.getText().toLowerCase().contains(actionPath[0].toLowerCase())) {
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
          if (actionMenuItem.getText().toLowerCase().contains(actionPath[0].toLowerCase())) {
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
            }, SHORT_TIMEOUT);
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
    return waitUntilFound(robot(), new GenericTypeMatcher<JBPopupMenu>(JBPopupMenu.class) {
      @Override
      protected boolean isMatching(@NotNull JBPopupMenu menu) {
        boolean found = false;
        for (MenuElement menuElement : menu.getSubElements()) {
          if (menuElement instanceof ActionMenu) {
            if (((ActionMenu)menuElement).getText().toLowerCase().equals(actionName.toLowerCase())) {
              found = true;
            }
          }
          else if (menuElement instanceof ActionMenuItem) {
            if (((ActionMenuItem)menuElement).getText().toLowerCase().equals(actionName.toLowerCase())) {
              found = true;
            }
          }
        }
        return found;
      }
    });
  }
}
