package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.RdTarget
import com.intellij.openapi.diagnostic.logger
import kotlin.time.Duration.Companion.seconds

fun Driver.getRunContentManager(project: Project): RunContentManager {
  return service<RunContentManager>(project, RdTarget.BACKEND)
}

fun Driver.tryToKillAllStartedProcesses() {
  getOpenProjects(RdTarget.BACKEND).forEach { project ->
    getRunContentManager(project).getAllDescriptors().forEach {
      val process = it.getProcessHandler()
      if (process != null) {
        runCatching {
          process.destroyProcess()
          process.waitFor(5.seconds.inWholeMilliseconds)
        }.onFailure {
          logger<Driver>().warn("failed to kill process: $process")
        }
      }
    }
  }
}

@Remote("com.intellij.execution.ui.RunContentManager")
interface RunContentManager {
  fun getAllDescriptors(): List<RunContentDescriptorRef>
  fun getSelectedContent(): RunContentDescriptorRef?
}

@Remote("com.intellij.execution.ui.RunContentDescriptor")
interface RunContentDescriptorRef {
  fun getDisplayName(): String
  fun getProcessHandler(): ProcessHandlerRef?
  fun getExecutionId(): Long
}

@Remote("com.intellij.execution.process.ProcessHandler")
interface ProcessHandlerRef {
  fun isProcessTerminated(): Boolean
  fun isProcessTerminating(): Boolean
  fun waitFor(millis: Long): Boolean
  fun destroyProcess()
  fun getExitCode(): Int?
}
