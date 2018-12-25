// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.actionButton
import com.intellij.testGuiFramework.impl.popupMenu
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.step
import com.intellij.ui.popup.PopupFactoryImpl
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.timing.Pause
import javax.swing.JButton
import javax.swing.JList

/**
 * @author Artem.Gainanov
 */
class RunConfigurationListFixture(val myRobot: Robot, val myIde: IdeFrameFixture) {

  private val EDIT_CONFIGURATIONS: String = "Edit Configurations..."

  /**
   * Returns a list of available run configurations
   * except for "Edit configuration" and "save configuration"
   */
  fun getRunConfigurationList(): List<String> {
    showPopup()
    val list = myRobot.finder()
      .find(myIde.target()) { it is JList<*> } as JList<*>
    val returnList: MutableList<String> = mutableListOf()
    for (i in 0 until list.model.size) {
      returnList.add(i, list.model.getElementAt(i).toString())
    }
    //Close popup
    showPopup()
    return returnList.filter { it != EDIT_CONFIGURATIONS && !it.contains("Save ") }
  }


  /**
   * Opens up run configuration popup and chooses given RC
   * @param name
   */
  fun configuration(name: String): RunActionFixture {
    showPopup()
    myIde.popupMenu(name, Timeouts.minutes02).clickSearchedItem()
    return RunActionFixture()
  }

  /**
   * Trying to click on Edit Configurations attempt times.
   */
  fun editConfigurations(attempts: Int = 5) {
    step("click on 'Edit Configurations...' menu") {
      try {
        JButtonFixture(myRobot, addConfigurationButton()).click()
      }
      catch (e: ComponentLookupException) {
        step("initial attempt failed, search again") {
          for (i in 0 until attempts) {
            logInfo("attempt #$i")
            showPopup()
            Pause.pause(1000)
            if (getEditConfigurationsState()) {
              break
            }
            //Close popup
            showPopup()
          }
          myIde.popupMenu(EDIT_CONFIGURATIONS, Timeouts.minutes02).clickSearchedItem()
        }
      }
    }

  }

  /**
   * Click on Add Configuration button.
   * Available if no configurations are available in the RC list
   */
  fun addConfiguration() {
    JButtonFixture(myRobot, addConfigurationButton()).click()
  }

  inner class RunActionFixture {

    private fun clickActionButton(buttonName: String) {
      with(myIde) { actionButton(buttonName) }.click()
    }

    fun run() { clickActionButton("Run") }

    fun debug() { clickActionButton("Debug") }

    fun runWithCoverage() { clickActionButton("Run with Coverage") }

    fun stop() { clickActionButton("Stop") }
  }

  private fun showPopup() {
    step("show or close popup for list of run/debug configurations") {
      JButtonFixture(myRobot, getRunConfigurationListButton()).click()
    }
  }

  private fun addConfigurationButton(): JButton {
    return step("search 'Add Configuration...' button") {
      val button = myRobot.finder()
        .find(myIde.target()) {
          it is JButton
          && it.text == "Add Configuration..."
          && it.isShowing
        } as JButton
      logInfo("found button '$button'")
      return@step button
    }
  }

  private fun getRunConfigurationListButton(): JButton {
    return myRobot.finder()
      .find(myIde.target()) {
        it is JButton
        && it.parent.parent is ActionToolbarImpl
        && it.text != ""
        && it.isShowing
      } as JButton
  }

  private fun getEditConfigurationsState(): Boolean {
    return step("get '$EDIT_CONFIGURATIONS' state") {
      val list = myRobot.finder()
        .find(myIde.target()) { it is JList<*> } as JList<*>
      val actionItem = list.model.getElementAt(0) as PopupFactoryImpl.ActionItem
      logInfo("item '$EDIT_CONFIGURATIONS' is ${if(actionItem.isEnabled) "" else "NOT "}enabled")
      return@step actionItem.isEnabled
    }
  }

}