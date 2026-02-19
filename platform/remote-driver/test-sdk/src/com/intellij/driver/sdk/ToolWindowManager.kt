package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
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

fun Driver.closeToolWindow(id: String) {
  step("Close $id tool window") {
    withContext(OnDispatcher.EDT) {
      getToolWindow(id).hide()
    }
  }
}

fun Driver.openToolWindow(id: String) {
  step("Open $id tool window") {
    withContext(OnDispatcher.EDT) {
      getToolWindow(id).show()
    }
  }
}