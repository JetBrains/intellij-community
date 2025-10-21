package com.intellij.cce.java.test

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CompletableDeferred

internal data class RunConfigurationResults(
  val exitCode: Int,
  val output: String
) {
  companion object {
    suspend fun compute(f: (ProgramRunner.Callback) -> Unit): RunConfigurationResults {
      val deferred = CompletableDeferred<Int>()
      val sb = StringBuilder()

      val callback = ProgramRunner.Callback { descriptor ->
        LOG.info("processStarted $descriptor")
        val processHandler = descriptor.processHandler ?: error("processHandler is null")
        processHandler.addProcessListener(object : ProcessListener {
          override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            sb.append(event.text)
          }

          override fun processTerminated(event: ProcessEvent) {
            LOG.info("processTerminated. exitCode=${event.exitCode}")
            deferred.complete(event.exitCode)
          }

          override fun processNotStarted() {
            LOG.error("processNotStarted")
            deferred.complete(-1)
          }
        })
      }

      f(callback)

      LOG.info("await for process termination")

      val exitCode = deferred.await()

      return RunConfigurationResults(exitCode, sb.toString())
    }
  }
}
