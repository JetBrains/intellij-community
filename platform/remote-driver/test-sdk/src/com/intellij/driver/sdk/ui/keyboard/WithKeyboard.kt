package com.intellij.driver.sdk.ui.keyboard

import com.intellij.driver.sdk.ui.remote.SearchContext

interface WithKeyboard {
  val searchContext: SearchContext

  fun keyboard(keyboardActions: RemoteKeyboard.() -> Unit) {
    RemoteKeyboard(searchContext.robot).keyboardActions()
  }
}