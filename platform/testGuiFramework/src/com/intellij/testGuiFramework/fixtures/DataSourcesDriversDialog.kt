// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.step
import org.fest.swing.core.Robot
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.timing.Timeout
import java.awt.Container
import javax.swing.JDialog

class DataSourcesDriversDialog(robot: Robot, dialog: JDialog) : JDialogFixture(robot, dialog), ContainerFixture<JDialog> {

  companion object {
    const val title = "Data Sources and Drivers"
  }

  fun getDownloadLink(timeout: Timeout = Timeouts.seconds05): JEditorPaneFixture = jEditorPaneFixture("Download", timeout)
  fun getSwitchLink(timeout: Timeout = Timeouts.seconds05): JEditorPaneFixture = jEditorPaneFixture("Switch", timeout)

  fun addDataSource(dataSource: String, settings: DataSourcesDriversDialog.() -> Unit) {
    with(this) {
      val jButtons = waitUntilFoundList(target() as Container, ActionButton::class.java, timeout = Timeouts.seconds05) {
        it.isShowing && it.isVisible && it.action.templatePresentation.text == "Add" && it.parent is ActionToolbar
      }
      ActionButtonFixture(GuiRobotHolder.robot, jButtons.first()).click()
      popupMenu(dataSource).clickSearchedItem()
      settings()
    }
  }
}

fun GuiTestCase.dataSourcesDriversDialog(timeout: Timeout = Timeouts.defaultTimeout): DataSourcesDriversDialog {
  return step("search '${DataSourcesDriversDialog.title}' dialog") {
    DataSourcesDriversDialog(robot(), findDialog(DataSourcesDriversDialog.title, false, timeout))
  }
}
