// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.Predicate
import com.intellij.testGuiFramework.util.step
import org.fest.swing.core.Robot
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.timing.Timeout
import javax.swing.JDialog

class ChooseJpaClassesPackageDialog(robot: Robot, dialog: JDialog) : JDialogFixture(robot, dialog), ContainerFixture<JDialog> {

  companion object {
    const val title = "Choose JPA Classes Package"
  }

  fun GuiTestCase.addPackage(name: String, timeout: Timeout = Timeouts.defaultTimeout) {
    actionButton("New Package...").click()
    findDialog("New Package", false, timeout).apply {
      message("New Package") {
        typeText(name)
        button("OK").clickWhenEnabled(Timeouts.seconds01)
      }
    }
  }

  fun GuiTestCase.addPackage(name: List<String>, timeout: Timeout = Timeouts.defaultTimeout) {
    addPackage(name.joinToString(separator = ".", prefix = "", postfix = ""), timeout)
  }

  fun GuiTestCase.selectPackage(name: String) {
    selectPackage(name.split("."))
  }

  fun GuiTestCase.selectPackage(name: List<String>) {
    val path = listOf("<default>", *name.toTypedArray()).toTypedArray()
    jTree(*path).clickPath()
    button("OK").click()
  }
}

fun GuiTestCase.chooseJpaClassesPackageDialog(timeout: Timeout = Timeouts.defaultTimeout): ChooseJpaClassesPackageDialog {
  return step("search '${ChooseJpaClassesPackageDialog.title}' dialog") {
    ChooseJpaClassesPackageDialog(robot(), findDialog(ChooseJpaClassesPackageDialog.title, false, timeout, Predicate.startWith))
  }
}

