package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.dialog

/** Clicks "Yes" in the "Confirm Package Installation" dialog if the dialog is shown. */
fun Finder.confirmPackageInstallationIfAsked() {
  val confirmation = dialog(title = "Confirm Package Installation")
  if (confirmation.present()) {
    confirmation.button("Yes").click()
  }
}
