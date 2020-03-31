// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.util.Ref
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.typeMatcher
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import org.fest.assertions.Assertions.assertThat
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.driver.JComponentDriver
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import org.fest.util.Lists.newArrayList
import java.awt.Container
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

class MenuFixture internal constructor(private val myRobot: Robot) {

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

    for (i in 0 until segmentCount) {
      val segment = path[i]
      val menuItem: JMenuItem = getMenuItem(null, pathIsRegex, segment, 2L)
      if (i < segmentCount - 1) {
        waitUntil("menu item $menuItem will be showing on screen") { menuItem.isShowing }
        myRobot.click(menuItem)
        continue
      }
      return menuItem
    }
    throw AssertionError("Menu item with path " + Arrays.toString(path) + " should have been found already")
  }

  private fun menuItemMatcher(pathIsRegex: Boolean,
                              segment: String): GenericTypeMatcher<JMenuItem> {
    return typeMatcher(JMenuItem::class.java) {
      it.parent !is IdeMenuBar &&
      it.width != 0 && it.height != 0 &&
      if (pathIsRegex) it.text.matches(segment.toRegex())
      else segment == it.text
    }
  }

  private fun getMenuItem(root: Container, pathIsRegex: Boolean, segment: String): JMenuItem {
    return myRobot.finder().find(root, menuItemMatcher(pathIsRegex, segment))
  }

  private fun getMenuItem(root: Container?, pathIsRegex: Boolean, segment: String, timeoutInSeconds: Long): JMenuItem {
    return GuiTestUtil.waitUntilFound(myRobot, root, menuItemMatcher(pathIsRegex, segment),
                                      Timeout.timeout(timeoutInSeconds, TimeUnit.SECONDS))
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
    selfType, robot, target) {

    init {
      replaceDriverWith(MenuItemFixtureDriver(robot))
    }

    fun isMenuItemChecked(): Boolean {
      val iconString = GuiTestUtilKt.computeOnEdt { target().icon?.toString() } ?: "null"
      return iconString.endsWith("checkmark.svg")
    }

    //wait for component showing on screen, as a workaround for IDEA-195830
    class MenuItemFixtureDriver(robot: Robot) : JComponentDriver<JMenuItem>(robot) {
      override fun click(jMenuItem: JMenuItem) {
        waitForShowing(jMenuItem, Timeouts.defaultTimeout.duration())
        robot.click(jMenuItem)
      }
    }
  }
}
