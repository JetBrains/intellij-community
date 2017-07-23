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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.*;
import java.awt.*;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.SHORT_TIMEOUT;
import static org.fest.swing.timing.Pause.pause;

public class JBListPopupFixture extends JComponentFixture<JBListPopupFixture, JBList> {

  private static final Logger LOG = Logger.getInstance("#com.intellij.tests.gui.fixtures.JBListPopupFixture");

  private JBList myJBList;
  private Robot myRobot;

  private JBListPopupFixture(JBList jbList, Robot robot) {
    super(JBListPopupFixture.class, robot, jbList);

    myJBList = jbList;
    myRobot = robot;
  }

  public static JBListPopupFixture findListPopup(Robot robot) {

    try {
      pause(new Condition("Find JBList popup") {
        @Override
        public boolean test() {
          final JBList jblist = getList(robot);
          return jblist != null;
        }
      }, SHORT_TIMEOUT);
    } catch (WaitTimedOutError e) {
      throw new ComponentLookupException("Cannot find JBList popup");
    }

    final JBList jblist = getList(robot);
    Assert.assertNotNull(jblist);

    return new JBListPopupFixture(jblist, robot);
  }

  @Nullable
  private static JBList getList(Robot robot) {
    try {
      JBList list = robot.finder().find(new GenericTypeMatcher<JBList>(JBList.class) {
        @Override
        protected boolean isMatching(@NotNull JBList list) {
          Container parent = list.getRootPane().getParent();
          if (parent == null) return false;
          if (parent instanceof JWindow && ((JWindow)parent).getType() == Window.Type.POPUP) {
            return true;
          }
          return false;
        }
      });
      return list;
    } catch (ComponentLookupException cle){
      return null;
    }
  }

  public void assertContainsAction(String actionName) {
    boolean assertion = false;
    ListModel listModel = myJBList.getModel();
    for (int i = 0; i < myJBList.getItemsCount(); i++) {
      Object elementAt = listModel.getElementAt(i);
      if (elementAt instanceof PopupFactoryImpl.ActionItem) {
        if (((PopupFactoryImpl.ActionItem)elementAt).getText().toLowerCase().contains(actionName.toLowerCase())) assertion = true;
      }
    }

    if (!assertion) LOG.error("Unable to find action \"" + actionName + "\" in popupList");
    assert assertion;
  }

  public void invokeAction(String actionName) {
    int actionIndex = -1;
    ListModel listModel = myJBList.getModel();
    for (int i = 0; i < myJBList.getItemsCount(); i++) {
      Object elementAt = listModel.getElementAt(i);
      if (elementAt instanceof PopupFactoryImpl.ActionItem) {
        if (((PopupFactoryImpl.ActionItem)elementAt).getText().toLowerCase().contains(actionName.toLowerCase())) actionIndex = i;
      }
    }

    if (actionIndex == -1) {
      LOG.error("Unable to find action \"" + actionName + "\" in popupMenu");
    } else {
      final Rectangle cellBounds = myJBList.getCellBounds(actionIndex, actionIndex);

      final Point locationOnScreen = myJBList.getLocationOnScreen();
      final Point point =
        new Point(locationOnScreen.x + cellBounds.x + cellBounds.width / 2,
                  locationOnScreen.y + cellBounds.y + cellBounds.height / 2);
      robot().click(point, MouseButton.LEFT_BUTTON, 1);
    }
  }
}
