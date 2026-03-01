package com.intellij.remoteDev.tests.impl

import com.intellij.openapi.diagnostic.DelegatingLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.remoteDev.tests.modelGenerated.RdTestSessionException
import com.intellij.remoteDev.tests.modelGenerated.RdTestSessionLightException
import com.intellij.remoteDev.tests.modelGenerated.RdTestSessionStackTraceElement
import com.jetbrains.rd.util.string.printToString

open class AgentTestLogger(logger: Logger, private val factory: AgentTestLoggerFactory) : DelegatingLogger<Logger>(logger) {
  override fun error(message: String?, t: Throwable?, vararg details: String) {
    super.error(message, t, *details)

    val testSession = factory.testSession.get()
    if (testSession == null) {
      logger<AgentTestLogger>().warn("Couldn't send exception to the test session: '${message}'.")
      return
    }
    val rdtseType = t?.javaClass?.typeName ?: "<LOG_ERROR>"
    val rdtseStackTrace = getRdtseStackTrace(t?.stackTrace)
    val rdtseCause = getRdtseCause(t)
    val rdtseSuppressedExceptions = getRdtseSuppressedExceptions(t)
    val details = if (details.isNotEmpty()) "Details: \n" + details.joinToString("\n") else null
    val rdTestSessionException = RdTestSessionException(type = rdtseType,
                                                        messageForDiogen = message,
                                                        messageForTestHistoryConsistency = getRdtseMessage(message, t),
                                                        printToStringForDiogen = (t ?: Exception(message)).printToString(),
                                                        messageWithDetails = listOfNotNull(message,
                                                                                           details,
                                                                                           t?.printToString()).joinToString("\n"),
                                                        stacktrace = rdtseStackTrace,
                                                        cause = rdtseCause,
                                                        suppressedExceptions = rdtseSuppressedExceptions)

    info("Fired ex to the test runner ${rdTestSessionException}")

    testSession.sendException.fire(rdTestSessionException)
  }

  private fun getRdtseStackTrace(stackTrace: Array<StackTraceElement>?): List<RdTestSessionStackTraceElement> =
    stackTrace?.map { RdTestSessionStackTraceElement(it.className, it.methodName, it.fileName.orEmpty(), it.lineNumber) }
    ?: emptyList()

  protected fun getRdtseMessage(message: String?, t: Throwable?): String =
    when (message) {
      t?.message ->
        message ?: "There was an error of type ${t?.javaClass?.name}"
      else ->
        listOf(message, t?.message).filter { !it?.trim().isNullOrEmpty() }.joinToString(": ")
    }

  private fun getRdtseCause(t: Throwable?): RdTestSessionLightException? =
    t?.cause?.let { cause ->
      RdTestSessionLightException(cause.javaClass.typeName, cause.message, getRdtseStackTrace(cause.stackTrace))
    }

  private fun getRdtseSuppressedExceptions(t: Throwable?): List<RdTestSessionLightException>? =
    t?.suppressedExceptions?.map { suppressedException ->
      RdTestSessionLightException(suppressedException.javaClass.typeName,
                                  suppressedException.message,
                                  stacktrace = getRdtseStackTrace(suppressedException.stackTrace))
    }
}
