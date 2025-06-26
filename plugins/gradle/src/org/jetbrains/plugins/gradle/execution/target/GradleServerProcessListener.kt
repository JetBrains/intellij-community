// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.text.nullize
import org.gradle.initialization.BuildEventConsumer
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.ResultHandler
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

internal class GradleServerProcessListener(
  private val targetProgressIndicator: GradleServerProgressIndicator,
  private val serverEnvironmentSetup: GradleServerEnvironmentSetup,
  private val resultHandler: ResultHandler<Any?>,
  private val connectorFactory: ToolingProxyConnector.ToolingProxyConnectorFactory,
  private val buildEventConsumer: BuildEventConsumer,
) : ProcessListener {

  @Volatile
  private var listenerJob: Future<*>? = null

  companion object {
    private const val CONNECTION_CONF_LINE_PREFIX = "Gradle target server hostAddress: "
    private val log = logger<GradleServerRunner>()
  }

  private val connectionAddressReceived: AtomicBoolean = AtomicBoolean(false)

  @RequiresBackgroundThread
  fun waitForServerShutdown() {
    listenerJob?.get()
  }

  override fun processTerminated(event: ProcessEvent) {
    val outputType = if (event.exitCode == 0) ProcessOutputType.STDOUT else ProcessOutputType.STDERR
    if (event.text != null) {
      targetProgressIndicator.addText(event.text, outputType)
    }
  }

  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    log.traceIfNotEmpty(event.text)
    if (connectionAddressReceived.get()) {
      return
    }
    if (outputType === ProcessOutputTypes.STDERR) {
      targetProgressIndicator.addText(event.text, outputType)
    }
    if (event.text.startsWith(CONNECTION_CONF_LINE_PREFIX)) {
      if (connectionAddressReceived.compareAndSet(false, true)) {
        val hostName = event.text.substringAfter(CONNECTION_CONF_LINE_PREFIX).substringBefore(" port: ")
        val port = event.text.substringAfter(" port: ").trim().toInt()
        runListener(hostName, port)
      }
    }
  }

  private fun Logger.traceIfNotEmpty(text: @NlsSafe String?) {
    text.nullize(true)?.also { trace { it.trimEnd() } }
  }

  private fun runListener(host: String, port: Int) {
    listenerJob = ApplicationManager.getApplication()
      .executeOnPooledThread {
        ProgressManager.getInstance()
          .executeProcessUnderProgress(
            {
              val toolingProxyConnection = connectorFactory.getConnector(host, port)
              toolingProxyConnection.collectAllEvents(
                serverEnvironmentSetup.getTargetBuildParameters(),
                resultHandler,
                buildEventConsumer,
                serverEnvironmentSetup.getTargetIntermediateResultHandler()
              )
            },
            targetProgressIndicator.progressIndicator
          )
      }
  }
}