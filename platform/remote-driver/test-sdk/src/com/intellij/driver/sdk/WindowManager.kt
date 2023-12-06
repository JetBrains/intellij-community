package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.sdk.ui.remote.Component

@Remote("com.intellij.openapi.wm.WindowManager")
interface WindowManager {
  fun getIdeFrame(project: Project): IdeFrame?
}

@Remote("com.intellij.openapi.wm.IdeFrame")
interface IdeFrame {
  fun getStatusBar(): StatusBar?

  fun getComponent(): Component?
}

@Remote("com.intellij.openapi.wm.ex.StatusBarEx")
interface StatusBar {
  fun getBackgroundProcesses(): List<TaskInfoPair>
  fun isProcessWindowOpen(): Boolean

  @Remote("com.intellij.openapi.util.Pair")
  interface TaskInfoPair {
    fun getFirst(): TaskInfo?
    fun getSecond(): ProgressIndicator?
  }
}

fun Driver.getIdeFrame(project: Project): IdeFrame? {
  return service<WindowManager>().getIdeFrame(project)
}