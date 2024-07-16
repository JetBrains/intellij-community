package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

fun Driver.getRunContentManager(project: Project) = service(RunContentManager::class, project)

@Remote("com.intellij.execution.ui.RunContentManager")
interface RunContentManager {
  fun getAllDescriptors(): List<RunContentDescriptorRef>
}

@Remote("com.intellij.execution.ui.RunContentDescriptor")
interface RunContentDescriptorRef {
  fun getDisplayName(): String
  fun getProcessHandler(): ProcessHandlerRef
}

@Remote("com.intellij.execution.process.ProcessHandler")
interface ProcessHandlerRef {
  fun isProcessTerminated(): Boolean
  fun isProcessTerminating(): Boolean
}
