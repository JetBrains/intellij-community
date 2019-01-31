// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.computeOnEdt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.runOnEdt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import com.intellij.testGuiFramework.impl.findComponent
import com.intellij.ui.JBListWithHintProvider
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupModel
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiActionRunner.execute
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JButtonFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.awt.Container
import javax.swing.JButton
import javax.swing.JList

class ComboBoxActionFixture(private val myRobot: Robot, private val myTarget: JButton) {

  val selectedItemText: String?
    get() = execute(object : GuiQuery<String>() {
      @Throws(Throwable::class)
      override fun executeInEDT(): String? {
        return myTarget.text
      }
    })

  private val popupList: JList<*>
    get() = myRobot.finder().findByType(JBListWithHintProvider::class.java)

  fun selectItem(itemName: String) {
    click()
    selectItemByText(popupList, itemName)
  }

  private fun click() {
    val comboBoxButtonFixture = JButtonFixture(myRobot, myTarget)
    waitUntil("ComboBoxButton will be enabled", Timeouts.minutes02) {
      computeOnEdt { comboBoxButtonFixture.target().isEnabled } ?: false
    }
    comboBoxButtonFixture.click()
  }

  companion object {

    private val ourComboBoxButtonClass: Class<*> by lazy {
      ComboBoxActionFixture::class.java.classLoader.loadClass(ComboBoxAction::class.java.canonicalName + "\$ComboBoxButton")
    }

    fun findComboBox(robot: Robot, root: Container): ComboBoxActionFixture {
      val comboBoxButton = robot.findComponent(root, JButton::class.java) {
        ourComboBoxButtonClass.isInstance(it)
      }
      return ComboBoxActionFixture(robot, comboBoxButton)
    }

    fun findComboBoxByText(robot: Robot, root: Container, text: String): ComboBoxActionFixture {
      val comboBoxButton = robot.findComponent(root, JButton::class.java) {
        ourComboBoxButtonClass.isInstance(it) && it.text == text
      }
      return ComboBoxActionFixture(robot, comboBoxButton)
    }

    private fun selectItemByText(list: JList<*>, text: String) {
      var actionItemIndex: Int = -1
      waitUntil("Wait until the list with action item (\"$text\") is populated.", Timeouts.minutes02) {
        computeOnEdt {
          val popupModel = list.model as ListPopupModel
          for (i in 0 until popupModel.size) {
            val actionItem = popupModel[i] as PopupFactoryImpl.ActionItem? ?: continue
            if (text == actionItem.text)
              list.selectedIndex = i
              return@computeOnEdt true
          }
          return@computeOnEdt false
        } ?: throw Exception ("Unable to compute on edt")
        actionItemIndex >= 0
      }

      assertTrue(actionItemIndex >= 0)

      runOnEdt { list.selectedIndex = actionItemIndex }
      assertEquals(text, (list.selectedValue as PopupFactoryImpl.ActionItem).text)
    }
  }
}
