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
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.testGuiFramework.cellReader.ExtendedJListCellReader
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.GuiTestUtil.waitUntilFound
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.list.ListPopupModel
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Timeout
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.junit.Assert.assertNotNull
import java.awt.Component
import java.awt.Container

class JBListPopupFixture private constructor(jbList: JBList<*>, robot: Robot) : JComponentFixture<JBListPopupFixture, JBList<*>>(
  JBListPopupFixture::class.java, robot, jbList) {

  private class PrefixMatcher(private val prefix: String) : BaseMatcher<String>() {

    override fun matches(item: Any): Boolean {
      return item is String && item.startsWith(prefix)
    }

    override fun describeTo(description: Description) {
      description.appendText("with prefix '$prefix'")
    }
  }

  private class EqualsMatcher(private val wanted: String) : BaseMatcher<String>() {

    override fun matches(item: Any): Boolean {
      return item is String && item == wanted
    }

    override fun describeTo(description: Description) {
      description.appendText("equals to '$wanted'")
    }
  }

  companion object {

    /**
     * Clicks an IntelliJ/Studio popup menu item with the label prefix
     *
     * @param label          the target menu item label prefix
     * @param searchByPrefix if false equality is checked, if true prefix is checked
     * @param component      a component in the same window that the popup menu is associated with
     * @param robot          the robot to drive it with
     */
    fun clickPopupMenuItem(label: String,
                           searchByPrefix: Boolean,
                           component: Component?,
                           robot: Robot,
                           timeout: Timeout) {
      val matcher = if (searchByPrefix) PrefixMatcher(label) else EqualsMatcher(label)
      clickPopupMenuItemMatching(matcher, component, robot, timeout)
    }

    fun getJListFixtureAndItemToClick(label: String,
                                      searchByPrefix: Boolean,
                                      container: Component?,
                                      robot: Robot,
                                      timeout: Timeout): Pair<JListFixture, Int> {
      val matcher = if (searchByPrefix) PrefixMatcher(label) else EqualsMatcher(label)
      return getJListFixtureAndClickableItemByMatcher(container as Container?, matcher, robot, timeout)
    }

    private fun clickPopupMenuItemMatching(labelMatcher: Matcher<String>,
                                           component: Component?,
                                           robot: Robot,
                                           timeout: Timeout) {
      // IntelliJ doesn't seem to use a normal JPopupMenu, so this won't work:
      //    JPopupMenu menu = myRobot.findActivePopupMenu();
      // Instead, it uses a JList (technically a JBList), which is placed somewhere
      // under the root pane.
      var root: Container? = null
      if (component != null) {
        root = GuiTestUtil.getRootContainer(component)
        assertNotNull(root)
      }

      val fixtureAndClickableItemPair = getJListFixtureAndClickableItemByMatcher(root, labelMatcher, robot, timeout)
      val (popupListFixture, clickableItem) = fixtureAndClickableItemPair
      popupListFixture.replaceCellReader(ExtendedJListCellReader())
      popupListFixture.clickItem(clickableItem)
    }


    private fun getJListFixtureAndClickableItemByMatcher(root: Container?,
                                                         labelMatcher: Matcher<String>,
                                                         robot: Robot,
                                                         timeout: Timeout): Pair<JListFixture, Int> {
      val fixtureAndClickableItemRef = Ref<Pair<JListFixture, Int>>()
      try {
        waitUntilFound<JBList<*>>(robot, root, object : GenericTypeMatcher<JBList<*>>(JBList::class.java) {
          override fun isMatching(list: JBList<*>): Boolean {
            val model = list.model
            if (model is ListPopupModel) {
              val fixtureAndClickableItem = getJListFixtureAndClickableItemByList(labelMatcher, robot, list)
              if (fixtureAndClickableItem != null) {
                fixtureAndClickableItemRef.set(fixtureAndClickableItem)
                return true
              }
            }
            return false
          }
        }, timeout)
      } catch (e: WaitTimedOutError){
        throw ComponentLookupException("Unable to get JListFixture because: ${e.message}")
      }
      return fixtureAndClickableItemRef.get() ?: throw ComponentLookupException("Unable to get JListFixture by matcher $labelMatcher")
    }

    private fun getJListFixtureAndClickableItemByList(labelMatcher: Matcher<String>,
                                                      robot: Robot,
                                                      list: JBList<*>): Pair<JListFixture, Int>? {
      val jListFixture = JListFixture(robot, list)
      jListFixture.replaceCellReader(ExtendedJListCellReader())
      val itemCount = jListFixture.target().model.size
      return (0 until itemCount)
        .firstOrNull { labelMatcher.matches(jListFixture.item(it).value()) }
        ?.let { Pair(jListFixture, it) }
    }


  }
}
