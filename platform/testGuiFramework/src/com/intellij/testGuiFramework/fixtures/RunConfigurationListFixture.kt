// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.ui.popup.PopupFactoryImpl
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.timing.Pause
import javax.swing.JButton
import javax.swing.JList

/**
 * @author Artem.Gainanov
 */
class RunConfigurationListFixture(val myRobot: Robot, val myIde: IdeFrameFixture) {

  private val EDIT_CONFIGURATIONS: String = "Edit Configurations..."
  var entryButton = JButtonFixture(myRobot, getRunConfigurationListButton())

  companion object {
    fun createRCListFixture(robot: Robot, ideFrame: IdeFrameFixture): RunConfigurationListFixture {
      return RunConfigurationListFixture(robot, ideFrame)
    }
  }


  /**
   * Returns a list of available run configurations
   * except for "Edit configuration" and "save configuration"
   */
  fun getRunConfigurationList(): List<String> {
    entryButton.click()
    val list = myRobot.finder()
      .find(myIde.target()) { it is JList<*> } as JList<*>
    val returnList: MutableList<String> = mutableListOf()
    for (i in 0 until list.model.size) {
      returnList.add(i, list.model.getElementAt(i).toString())
    }
    entryButton.click()
    return returnList.filter { it != EDIT_CONFIGURATIONS && !it.contains("Save ") }
  }


  /**
   * Opens up run configuration popup and chooses given RC
   * @param name
   */
  fun configuration(name: String): RunAction {
    entryButton.click()
    JBListPopupFixture.clickPopupMenuItem(name, false, null, myRobot, GuiTestUtil.SHORT_TIMEOUT)
    setConfigurationButton()
    return RunAction()
  }

  fun editConfigurations(attempts: Int = 5) {
    for (i in 0 until attempts) {
      entryButton.click()
      Pause.pause(1000)
      if (getEditConfigurationsState()) {
        break
      }
      entryButton.click()
    }
    JBListPopupFixture.clickPopupMenuItem(EDIT_CONFIGURATIONS, false, null, myRobot, GuiTestUtil.THIRTY_SEC_TIMEOUT)
  }

  inner class RunAction {
    fun run() {
      ActionButtonFixture.findByText("Run", myRobot, myIde.target()).click()
    }

    fun debug() {
      ActionButtonFixture.findByText("Debug", myRobot, myIde.target()).click()
    }

    fun runWithCoverage() {
      ActionButtonFixture.findByText("Run with Coverage", myRobot, myIde.target()).click()
    }

    fun stop() {
      ActionButtonFixture.findByText("Stop", myRobot, myIde.target()).click()

    }
  }

  private fun getRunConfigurationListButton(): JButton {
    return myRobot.finder()
      .find(myIde.target()) {
        it is JButton
        && it.parent.parent is ActionToolbarImpl && it.text != ""
      } as JButton
  }

  private fun setConfigurationButton() {
    entryButton = JButtonFixture(myRobot, getRunConfigurationListButton())
  }


  private fun getEditConfigurationsState(): Boolean {
    val list = myRobot.finder()
      .find(myIde.target()) { it is JList<*> } as JList<*>
    val actionItem = list.model.getElementAt(0) as PopupFactoryImpl.ActionItem
    return actionItem.isEnabled
  }

}