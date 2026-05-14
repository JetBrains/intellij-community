package com.intellij.driver.sdk

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.remote.ColorRef

@Remote("com.intellij.ui.SimpleTextAttributes")
interface SimpleTextAttributes {
  fun getFgColor(): ColorRef?
  fun isWaved(): Boolean
}
