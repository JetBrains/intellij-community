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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.util.Lists.newArrayList;
import static org.junit.Assert.assertNotNull;

class MenuFixture {
  @NotNull private final Robot myRobot;
  @NotNull private final IdeFrameImpl myContainer;

  MenuFixture(@NotNull Robot robot, @NotNull IdeFrameImpl container) {
    myRobot = robot;
    myContainer = container;
  }

  /**
   * Invokes an action by menu path
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project ")}
   */
  void invokeMenuPath(@NotNull String... path) {
    JMenuItem menuItem = findActionMenuItem(false, path);
    myRobot.click(menuItem);
  }

  /**
   * Invokes an action by menu path (where each segment is a regular expression). This is particularly
   * useful when the menu items can change dynamically, such as the labels of Undo actions, Run actions,
   * etc.
   *
   * @param path the series of menu name regular expressions, e.g. {@link invokeActionByMenuPath("Build", "Make( Project)?")}
   */
  void invokeMenuPathRegex(@NotNull String... path) {
    JMenuItem menuItem = findActionMenuItem(true, path);
    myRobot.click(menuItem);
  }

  @NotNull
  private JMenuItem findActionMenuItem(final boolean pathIsRegex, @NotNull String... path) {
    assertThat(path).isNotEmpty();
    int segmentCount = path.length;

    // We keep the list of previously found pop-up menus, so we don't look for menu items in the same pop-up more than once.
    List<JPopupMenu> previouslyFoundPopups = new ArrayList<>();

    Container root = myContainer;
    for (int i = 0; i < segmentCount; i++) {
      final String segment = path[i];
      assertNotNull(root);
      JMenuItem found = myRobot.finder().find(root, new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
        @Override
        protected boolean isMatching(@NotNull JMenuItem menuItem) {
          return pathIsRegex ? menuItem.getText().matches(segment) : segment.equals(menuItem.getText());
        }
      });
      if (root instanceof JPopupMenu) {
        previouslyFoundPopups.add((JPopupMenu)root);
      }
      if (i < segmentCount - 1) {
        List<JPopupMenu> showingPopupMenus = findShowingPopupMenus(getCountOfShowing(previouslyFoundPopups) + 1);
        myRobot.click(found);
        showingPopupMenus.removeAll(previouslyFoundPopups);
        assertThat(showingPopupMenus).hasSize(1);
        root = showingPopupMenus.get(0);
        continue;
      }
      return found;
    }
    throw new AssertionError("Menu item with path " + Arrays.toString(path) + " should have been found already");
  }

  private int getCountOfShowing(List<JPopupMenu> previouslyFoundPopups) {
    return (int)previouslyFoundPopups.stream().filter(popupMenu -> popupMenu.isShowing()).count();
  }

  @NotNull
  private List<JPopupMenu> findShowingPopupMenus(final int expectedCount) {
    final Ref<List<JPopupMenu>> ref = new Ref<List<JPopupMenu>>();
    Pause.pause(new Condition("waiting for " + expectedCount + " JPopupMenus to show up") {
      @Override
      public boolean test() {
        List<JPopupMenu> popupMenus = newArrayList(myRobot.finder().findAll(new GenericTypeMatcher<JPopupMenu>(JPopupMenu.class) {
          @Override
          protected boolean isMatching(@NotNull JPopupMenu popupMenu) {
            return popupMenu.isShowing();
          }
        }));
        boolean allFound = popupMenus.size() == expectedCount;
        if (allFound) {
          ref.set(popupMenus);
        }
        return allFound;
      }
    });
    List<JPopupMenu> popupMenus = ref.get();
    assertThat(popupMenus).isNotNull().hasSize(expectedCount);
    return popupMenus;
  }
}
