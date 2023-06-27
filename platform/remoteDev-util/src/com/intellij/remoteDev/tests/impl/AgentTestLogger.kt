package com.intellij.remoteDev.tests.impl

import com.intellij.openapi.diagnostic.DelegatingLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.remoteDev.tests.modelGenerated.RdTestSession
import com.intellij.remoteDev.tests.modelGenerated.RdTestSessionException
import com.intellij.remoteDev.tests.modelGenerated.RdTestSessionExceptionCause
import com.intellij.remoteDev.tests.modelGenerated.RdTestSessionStackTraceElement

open class AgentTestLogger(logger: Logger, private val factory: AgentTestLoggerFactory) : DelegatingLogger<Logger>(logger) {
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

  protected open fun sendToTestRunner(session: RdTestSession, message: String?, t: Throwable?) {
    val rdtseMessage: String = getRdtseMessage(message, t)
    val rdtseType = getRdtseType(t)
    val rdtseStackTrace = getRdtseStackTrace(t?.stackTrace)
    val rdtseCause = getRdtseCause(t)
    val rdTestSessionException = RdTestSessionException(type = rdtseType, originalType = null, message = rdtseMessage,
                                                        stacktrace = rdtseStackTrace, cause = rdtseCause)

    info("Fired ex to the test runner ${rdTestSessionException.message}")

    session.sendException.fire(rdTestSessionException)
  }

  protected fun getRdtseStackTrace(stackTrace: Array<StackTraceElement>?): List<RdTestSessionStackTraceElement> =
    stackTrace?.map { RdTestSessionStackTraceElement(it.className, it.methodName, it.fileName.orEmpty(), it.lineNumber) }
    ?: emptyList()

  protected fun getRdtseMessage(message: String?, t: Throwable?) =
    when (message) {
      t?.message ->
        message ?: "There was an error of type ${t?.javaClass?.name}"
      else ->
        listOf(message, t?.message).filter { !it?.trim().isNullOrEmpty() }.joinToString(": ")
    }

  protected fun getRdtseType(t: Throwable?) = t?.javaClass?.typeName ?: "<LOG_ERROR>"
  protected fun getRdtseCause(t: Throwable?) =
    t?.cause?.let { cause ->
      RdTestSessionExceptionCause(cause.javaClass.typeName, cause.message, getRdtseStackTrace(cause.stackTrace))
    }

}
