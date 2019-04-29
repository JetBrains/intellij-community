// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.step
import org.fest.swing.core.Robot
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.timing.Timeout
import javax.swing.JDialog

class ImportDatabaseSchemaDialog(robot: Robot, dialog: JDialog) : JDialogFixture(robot, dialog), ContainerFixture<JDialog> {

  companion object {
    const val title = "Import Database Schema"
  }

  val chooseDataSourceChooser get() = combobox("Choose Data Source:")
  val dataSourceButton get() = buttonsByIcon("ellipsis.svg", Timeouts.seconds02)[0]

  val packageChooser get() = combobox("Package:")
  val packageButton get() = buttonsByIcon("ellipsis.svg", Timeouts.seconds02)[1]

  val entityPrefix get() = textfield("Entity prefix:")
  val entitySuffix get() = textfield("Entity suffix:")
  val preferPrimitiveTypes get() = checkbox("Prefer primitive types")
  val showDefaultRelationships get() = checkbox("Show default relationships")

  val generateColumnProperties get() = checkbox("Generate Column Properties")
  val generateSeparateXml get() = checkbox("Generate Separate XML per Entity")

  fun GuiTestCase.addDataSource(datasource: String, actions: DataSourcesDriversDialog.() -> Unit) {
    step("click button '...' to show dialog 'Data Sources and Drivers''") {
      dataSourceButton.clickWhenEnabled(timeout = Timeouts.noTimeout)
    }
    with(dataSourcesDriversDialog()) {
      step("at '${DataSourcesDriversDialog.title}' dialog") {
        addDataSource(datasource) {
          actions()
        }
      }
    }
  }

  fun GuiTestCase.setPackage(actions: ChooseJpaClassesPackageDialog.() -> Unit) {
    step("click button '...' to show '${ChooseJpaClassesPackageDialog.title}' dialog'") {
      packageButton.clickWhenEnabled(timeout = Timeouts.noTimeout)
    }
    with(chooseJpaClassesPackageDialog()) {
      step("at '${ChooseJpaClassesPackageDialog.title}' dialog") {
        actions()
      }
    }
  }

  fun GuiTestCase.setPackage(name: String) {
    setPackage {
      addPackage(name)
    }
  }

  fun GuiTestCase.makeSureInFocus(timeout: Timeout = Timeouts.defaultTimeout) {
    val currentTitle = this@ImportDatabaseSchemaDialog.target().title
    step("wait for dialog with title '$currentTitle' focused") {
      waitUntilFound(null, JDialog::class.java, timeout) {
        it.isShowing && it.isEnabled && it.isVisible && it.isFocused
        && (it.title == currentTitle)
      }
    }
  }

}

fun GuiTestCase.importDatabaseSchemaDialog(timeout: Timeout = Timeouts.defaultTimeout): ImportDatabaseSchemaDialog {
  return step("Search 'Import database schema' dialog") {
    ImportDatabaseSchemaDialog(robot(), findDialog(ImportDatabaseSchemaDialog.title, false, timeout))
  }
}

fun GuiTestCase.importDatabaseSchemaDialog(timeout: Timeout = Timeouts.defaultTimeout,
                                           actions: ImportDatabaseSchemaDialog.() -> Unit): ImportDatabaseSchemaDialog {
  return importDatabaseSchemaDialog(timeout).apply {
    step("at 'Import database schema' dialog") { actions() }
  }
}

