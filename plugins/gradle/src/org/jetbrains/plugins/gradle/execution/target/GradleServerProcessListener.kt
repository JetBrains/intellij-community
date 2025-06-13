// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.nullize
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler

internal class GradleServerProcessListener(
  private val targetProgressIndicator: TargetProgressIndicator,
  private val resultHandler: ResultHandler<Any?>,
  private val gradleServerEventsListener: GradleServerEventsListener,
) : ProcessListener {

  companion object {
    private const val CONNECTION_CONF_LINE_PREFIX = "Gradle target server hostAddress: "
    private val log = logger<GradleServerRunner>()
  }

  @Volatile
  private var connectionAddressReceived = false

  @Volatile
  var resultReceived = false

  val resultHandlerWrapper: ResultHandler<Any?> = object : ResultHandler<Any?> {
    override fun onComplete(result: Any?) {
      resultReceived = true
      resultHandler.onComplete(result)
    }

    override fun onFailure(gradleConnectionException: GradleConnectionException) {
      resultReceived = true
      resultHandler.onFailure(gradleConnectionException)
    }
  }

  override fun processTerminated(event: ProcessEvent) {
    if (!resultReceived) {
      gradleServerEventsListener.waitForResult { resultReceived || targetProgressIndicator.isCanceled }
    }
    if (!resultReceived) {
      val outputType = if (event.exitCode == 0) ProcessOutputType.STDOUT else ProcessOutputType.STDERR
      event.text?.also { targetProgressIndicator.addText(it, outputType) }
      val gradleConnectionException = if (targetProgressIndicator.isCanceled) {
        BuildCancelledException("Build cancelled.")
      }
      else {
        GradleConnectionException("Operation result has not been received.")
      }
      resultHandler.onFailure(gradleConnectionException)
    }
    gradleServerEventsListener.stop()
  }

  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    log.traceIfNotEmpty(event.text)
    if (connectionAddressReceived) return
    if (outputType === ProcessOutputTypes.STDERR) {
      targetProgressIndicator.addText(event.text, outputType)
    }
    if (event.text.startsWith(CONNECTION_CONF_LINE_PREFIX)) {
      connectionAddressReceived = true
      val hostName = event.text.substringAfter(CONNECTION_CONF_LINE_PREFIX).substringBefore(" port: ")
      val port = event.text.substringAfter(" port: ").trim().toInt()
      gradleServerEventsListener.start(hostName, port, resultHandlerWrapper)
    }
  }

  private fun Logger.traceIfNotEmpty(text: @NlsSafe String?) {
    text.nullize(true)?.also { trace { it.trimEnd() } }
  }
}
