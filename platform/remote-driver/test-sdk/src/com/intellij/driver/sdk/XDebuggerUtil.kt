package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher

@Remote(value = "com.intellij.xdebugger.XDebuggerUtil")
interface XDebuggerUtil {
  fun toggleLineBreakpoint(project: Project,
                           file: VirtualFile,
                           line: Int,
                           temporary: Boolean)
}

fun Driver.toggleLineBreakpoint(filePath: String, line: Int) {
  withContext(OnDispatcher.EDT) {
    val debuggerUtil = service<XDebuggerUtil>()
    val file = findFile(singleProject(), filePath) ?: error("Invalid file path: $filePath")
    debuggerUtil.toggleLineBreakpoint(singleProject(), file, line, false)
  }
}
