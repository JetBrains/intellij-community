/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Timeout;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.SHORT_TIMEOUT;
import static com.intellij.testGuiFramework.framework.GuiTestUtil.waitUntilFound;
import static org.junit.Assert.assertNotNull;

public class JBListPopupFixture extends JComponentFixture<JBListPopupFixture, JBList> {

  private static final Logger LOG = Logger.getInstance("#com.intellij.tests.gui.fixtures.JBListPopupFixture");

  private JBListPopupFixture(JBList jbList, Robot robot) {
    super(JBListPopupFixture.class, robot, jbList);
  }

  /**
   * Waits until an IDE popup is shown (and returns it
   */
  public static JBList waitForPopup(@NotNull Robot robot) {
    return GuiTestUtil.waitUntilFound(robot, null, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        ListModel model = list.getModel();
        return model instanceof ListPopupModel;
      }
    });
  }

  /**
   * Clicks an IntelliJ/Studio popup menu item with the given label prefix
   *
   * @param labelPrefix the target menu item label prefix
   * @param component   a component in the same window that the popup menu is associated with
   * @param robot       the robot to drive it with
   */
  public static void clickPopupMenuItem(@NotNull String labelPrefix, @NotNull Component component, @NotNull Robot robot) {
    clickPopupMenuItemMatching(new GuiTestUtil.PrefixMatcher(labelPrefix), component, robot);
  }

  public static void clickPopupMenuItem(@NotNull String label, boolean searchByPrefix, @NotNull Component component, @NotNull Robot robot) {
    if (searchByPrefix) {
      clickPopupMenuItemMatching(new GuiTestUtil.PrefixMatcher(label), component, robot);
    }
    else {
      clickPopupMenuItemMatching(new GuiTestUtil.EqualsMatcher(label), component, robot);
    }
  }

  public static void clickPopupMenuItem(@NotNull String label, boolean searchByPrefix, @NotNull Component component, @NotNull Robot robot, @NotNull Timeout timeout) {
    if (searchByPrefix) {
      clickPopupMenuItemMatching(new GuiTestUtil.PrefixMatcher(label), component, robot, timeout);
    }
    else {
      clickPopupMenuItemMatching(new GuiTestUtil.EqualsMatcher(label), component, robot, timeout);
    }
  }

  public static void clickPopupMenuItemMatching(@NotNull Matcher<String> labelMatcher, @NotNull Component component, @NotNull Robot robot) {
    clickPopupMenuItemMatching(labelMatcher, component, robot, SHORT_TIMEOUT);
  }

  public static void clickPopupMenuItemMatching(@NotNull Matcher<String> labelMatcher, @NotNull Component component, @NotNull Robot robot, @NotNull Timeout timeout) {
    // IntelliJ doesn't seem to use a normal JPopupMenu, so this won't work:
    //    JPopupMenu menu = myRobot.findActivePopupMenu();
    // Instead, it uses a JList (technically a JBList), which is placed somewhere
    // under the root pane.
    Container root = GuiTestUtil.getRootContainer(component);
    assertNotNull(root);


    Ref<Pair<JListFixture, Integer>> fixtureAndClickableItemRef = new Ref<>();
    // First find the JBList which holds the popup. There could be other JBLists in the hierarchy,
    // so limit it to one that is actually used as a popup, as identified by its model being a ListPopupModel:
    waitUntilFound(robot, root,  new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        ListModel model = list.getModel();
        if (model instanceof ListPopupModel) {
          Pair<JListFixture, Integer> fixtureAndClickableItem = getJListFixtureAndClickableItem(labelMatcher, robot, list);
          if (fixtureAndClickableItem != null) {
            fixtureAndClickableItemRef.set(fixtureAndClickableItem);
            return true;
          }
        }
        return false;
      }
    }, timeout);

    Pair<JListFixture, Integer> fixtureAndClickableItemPair = fixtureAndClickableItemRef.get();
    JListFixture popupListFixture = fixtureAndClickableItemPair.first;
    int clickableItem = fixtureAndClickableItemPair.second;
    popupListFixture.clickItem(clickableItem);
  }

  @Nullable
  private static Pair<JListFixture, Integer> getJListFixtureAndClickableItem(@NotNull Matcher<String> labelMatcher, @NotNull Robot robot, JBList list) {
    ListPopupModel model = (ListPopupModel)list.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      String popupItem = readPopupItem(model, i);
      if (labelMatcher.matches(popupItem)) {
        return new Pair<>(new JListFixture(robot, list), i);
      }
    }
    return null;
  }

  @NotNull
  private static String readPopupItem(ListPopupModel model, int itemNumber) {
    assert itemNumber < model.getSize();
    Object elementAt = model.getElementAt(itemNumber);
    if (elementAt instanceof PopupFactoryImpl.ActionItem) {
      return ((PopupFactoryImpl.ActionItem)elementAt).getText();
    } else if(elementAt instanceof ConfigurationTypeBase){
      return ((ConfigurationTypeBase)elementAt).getDisplayName();
    }
    else { // For example package private class IntentionActionWithTextCaching used in quickfix popups
      return elementAt.toString();
    }

  }
}
