package com.intellij.driver.sdk.python.steps

import com.intellij.driver.sdk.ManualWaitForIndicators
import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.python.pythonPackagesToolWindow
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForIndicators
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The inline link of an installable row. A click on it installs the package without a dialog. */
private const val INSTALL_LINK_TEXT = "Install"

/** The tool window action that uninstalls the selected installed packages. */
private const val UNINSTALL_ACTION_ID = "PyDeletePackage"

val IdeaFrameUI.packages get() = PackagesSteps(this)

class PackagesSteps(private val ideFrame: IdeaFrameUI) {

  // The redesigned tool window has no Install or Uninstall button and no row context menu. An installable row
  // shows an inline "Install" link, and an installed row shows an unnamed uninstall icon (PyPackagesTree).
  fun installPackage(pyPackage: String) {
    ideFrame.run {
      rightToolWindowToolbar.stripeButton("Python Packages").open()
      pythonPackagesToolWindow().waitFound(10.seconds).apply {
        searchField.click()
        driver.ui.pasteText(pyPackage)
        packagesTable.waitAnyTexts(pyPackage, timeout = 30.seconds).first().click()
        packagesTable.waitOneText(INSTALL_LINK_TEXT).click()
      }
    }
  }

  @OptIn(ManualWaitForIndicators::class)
  fun uninstallPackage(pyPackage: String) {
    ideFrame.run {
      rightToolWindowToolbar.stripeButton("Python Packages").open()
      pythonPackagesToolWindow().waitFound(10.seconds).apply {
        // An empty search shows the installed packages only, so the row is the installed one.
        searchField.click()
        keyboard {
          hotKeyWithDefaultModifierKey(KeyEvent.VK_A)
          backspace()
        }
        packagesTable.waitAnyTexts(pyPackage, timeout = 30.seconds).first().click()
        // The action reads the selected packages from the data context of the tool window panel.
        driver.invokeAction(UNINSTALL_ACTION_ID, component = packagesTable.component)
        driver.waitForIndicators(2.minutes)
      }
    }
  }

  fun isPackageInstalled(pyPackage: String): Boolean {
    var isPackageFound = false
    ideFrame.run {
      rightToolWindowToolbar.stripeButton("Python Packages").open()
      pythonPackagesToolWindow().waitFound(10.seconds).apply {
        // The installed-packages list loads asynchronously ("Loading Package info..."), so reading it
        // once can miss a package that is still loading. Wait until the package shows up (PCQA-1760).
        isPackageFound = try {
          packagesTable.waitAnyTextsContains(pyPackage, timeout = 2.minutes)
          true
        }
        catch (_: WaitForException) {
          false
        }
      }
      return isPackageFound
    }
  }
}
