package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.Window

@Remote("com.intellij.openapi.wm.WindowManager")
interface WindowManager {
  fun getIdeFrame(project: Project): IdeFrame?
  fun findVisibleFrame(): IdeFrame?
  fun getMostRecentFocusedWindow(): IdeFrame?
}

@Remote("com.intellij.openapi.wm.IdeFrame")
interface IdeFrame {
  fun getStatusBar(): StatusBar?
  fun getProject(): Project?
  fun getComponent(): Component?
}

@Remote("com.intellij.openapi.wm.ex.StatusBarEx")
interface StatusBar {
  fun getBackgroundProcessModels(): List<Pair<TaskInfo?, ProgressModel?>>
  fun isProcessWindowOpen(): Boolean
}

fun Driver.getIdeFrame(project: Project): IdeFrame? {
  return service<WindowManager>().getIdeFrame(project)
}

fun Driver.guessOpenedProject(): Project? {
  return service<WindowManager>().getMostRecentFocusedWindow()?.getProject()
}

fun Driver.hasVisibleWindow(): Boolean {
  return utility(Window::class).getWindows().any { window -> window.isShowing() }
}