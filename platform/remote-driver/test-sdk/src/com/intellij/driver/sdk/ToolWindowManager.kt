package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import org.jetbrains.annotations.NonNls

@Remote("com.intellij.openapi.wm.ToolWindowManager")
interface ToolWindowManager {
   fun getToolWindow(@NonNls id: String): ToolWindow?

  fun getInstance(project: Project): ToolWindowManager
}

@Remote("com.intellij.openapi.wm.ToolWindow")
interface ToolWindow {
  fun show()
  fun hide()
  fun isVisible(): Boolean
}

fun Driver.getToolWindow(id: String): ToolWindow {
  val manager = utility(ToolWindowManager::class).getInstance(singleProject())
  return manager.getToolWindow(id)!!
}