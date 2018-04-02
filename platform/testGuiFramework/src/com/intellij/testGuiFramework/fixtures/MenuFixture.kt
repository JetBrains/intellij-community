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
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.util.Ref
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.typeMatcher
import org.fest.assertions.Assertions.assertThat
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import org.fest.util.Lists.newArrayList
import org.junit.Assert.assertNotNull
import java.awt.Container
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

class MenuFixture internal constructor(private val myRobot: Robot, private val myContainer: IdeFrameImpl) {

  /**
   * Invokes an action by menu path
   *
   * @param path the series of menu names, e.g. [&quot;][]
   */
  fun invokeMenuPath(vararg path: String) {
    getMenuItemFixture(*path).click()
  }

  fun getMenuItemFixture(vararg path: String): MenuItemFixture {
    return MenuItemFixture(MenuItemFixture::class.java, myRobot, findActionMenuItem(false, *path))
  }

  fun getMenuItemFixtureByRegex(vararg path: String): MenuItemFixture {
    return MenuItemFixture(MenuItemFixture::class.java, myRobot, findActionMenuItem(true, *path))
  }

  /**
   * Invokes an action by menu path (where each segment is a regular expression). This is particularly
   * useful when the menu items can change dynamically, such as the labels of Undo actions, Run actions,
   * etc.
   *
   * @param path the series of menu name regular expressions, e.g. [&quot;][]
   */
  fun invokeMenuPathRegex(vararg path: String) {
    getMenuItemFixtureByRegex(*path).click()
  }

  private fun findActionMenuItem(pathIsRegex: Boolean, vararg path: String): JMenuItem {
    assertThat(path).isNotEmpty
    val segmentCount = path.size

    // We keep the list of previously found pop-up menus, so we don't look for menu items in the same pop-up more than once.
    val previouslyFoundPopups = ArrayList<JPopupMenu>()

    var root: Container = myContainer
    for (i in 0 until segmentCount) {
      val segment = path[i]
      assertNotNull(root)
      val menuItem: JMenuItem = getMenuItem(root, pathIsRegex, segment, 2L)
      if (root is JPopupMenu) {
        previouslyFoundPopups.add(root)
      }
      if (i < segmentCount - 1) {
        val showingPopupMenus = findShowingPopupMenus(getCountOfShowing(previouslyFoundPopups) + 1)
        myRobot.click(menuItem)
        showingPopupMenus.removeAll(previouslyFoundPopups)
        assertThat(showingPopupMenus).hasSize(1)
        root = showingPopupMenus[0]
        continue
      }
      return menuItem
    }
    throw AssertionError("Menu item with path " + Arrays.toString(path) + " should have been found already")
  }

  private fun menuItemMatcher(pathIsRegex: Boolean,
                              segment: String): GenericTypeMatcher<JMenuItem> {
    return typeMatcher(JMenuItem::class.java, {
      if (pathIsRegex) it.text.matches(segment.toRegex())
      else segment == it.text
    })
  }

  private fun getMenuItem(root: Container, pathIsRegex: Boolean, segment: String): JMenuItem {
    return myRobot.finder().find(root, menuItemMatcher(pathIsRegex, segment))
  }

  private fun getMenuItem(root: Container, pathIsRegex: Boolean, segment: String, timeoutInSeconds: Long): JMenuItem {
    return GuiTestUtil.waitUntilFound(myRobot, root, menuItemMatcher(pathIsRegex, segment), Timeout.timeout(timeoutInSeconds, TimeUnit.SECONDS))
  }

  private fun getCountOfShowing(previouslyFoundPopups: List<JPopupMenu>): Int {
    return previouslyFoundPopups.stream().filter { popupMenu -> popupMenu.isShowing }.count().toInt()
  }

  private fun findShowingPopupMenus(expectedCount: Int): MutableList<JPopupMenu> {
    val ref = Ref<MutableList<JPopupMenu>>()
    Pause.pause(object : Condition("waiting for $expectedCount JPopupMenus to show up") {
      override fun test(): Boolean {
        val popupMenus = newArrayList(myRobot.finder().findAll(typeMatcher(JPopupMenu::class.java, { it.isShowing })))
        val allFound = popupMenus.size == expectedCount
        if (allFound)
          ref.set(popupMenus)
        return allFound
      }
    })
    val popupMenus = ref.get()
    assertThat(popupMenus).isNotNull.hasSize(expectedCount)
    return popupMenus
  }

  class MenuItemFixture(selfType: Class<MenuItemFixture>, robot: Robot, target: JMenuItem) : JComponentFixture<MenuItemFixture, JMenuItem>(
    selfType, robot, target)
}
