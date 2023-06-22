package com.intellij.remoteDev.tests.impl

import com.intellij.openapi.diagnostic.DelegatingLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.remoteDev.tests.modelGenerated.RdTestSession
import com.intellij.remoteDev.tests.modelGenerated.RdTestSessionException
import com.intellij.remoteDev.tests.modelGenerated.RdTestSessionExceptionCause
import com.intellij.remoteDev.tests.modelGenerated.RdTestSessionStackTraceElement

internal class AgentTestLogger(logger: Logger, private val factory: AgentTestLoggerFactory) : DelegatingLogger<Logger>(logger) {
  override fun error(message: String?, t: Throwable?, vararg details: String) {
    super.error(message, t, *details)
    val messageToTheTestRunner =
      if (details.isNotEmpty()) message + "\n Details: \n" + details.joinToString("\n")
      else message

    factory.testSession.get()
      ?.let {
        sendToTestRunner(it, messageToTheTestRunner, t)
      }
    ?: logger<AgentTestLogger>().warn("Couldn't send exception to the test session: '${message}'.")
  }

  private fun sendToTestRunner(session: RdTestSession, message: String?, t: Throwable?) {
    fun getRdStackTrace(stackTrace: Array<StackTraceElement>?): List<RdTestSessionStackTraceElement> =
      stackTrace?.map { RdTestSessionStackTraceElement(it.className, it.methodName, it.fileName.orEmpty(), it.lineNumber) }
      ?: emptyList()

    val rdtseMessage: String =
      when (message) {
        t?.message ->
          message ?: "There was an error of type ${t?.javaClass?.name}"
        else ->
          listOf(message, t?.message).filter { !it?.trim().isNullOrEmpty() }.joinToString(": ")
      }

    val rdtseType = t?.javaClass?.typeName ?: "<LOG_ERROR>"
    val rdtseStackTrace = getRdStackTrace(t?.stackTrace)
    val rdtseCause = t?.cause?.let { cause ->
      RdTestSessionExceptionCause(cause.javaClass.typeName, cause.message, getRdStackTrace(cause.stackTrace))
    }

    val rdTestSessionException = RdTestSessionException(rdtseType, rdtseMessage, rdtseStackTrace, rdtseCause)

    info("Fired ex to the test runner ${rdTestSessionException.message}")
    session.sendException.fire(rdTestSessionException)
  }
}
