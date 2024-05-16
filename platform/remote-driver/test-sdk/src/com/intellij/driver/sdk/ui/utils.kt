package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.remote.Component

fun Driver.hasFocus(c: Component) = utility(IJSwingUtilities::class).hasFocus(c)

@Remote("com.intellij.util.IJSwingUtilities")
interface IJSwingUtilities {
  fun hasFocus(c: Component): Boolean
}

