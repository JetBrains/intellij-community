package com.intellij.remoteDev.util.tests.impl

import com.intellij.openapi.diagnostic.DelegatingLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.remoteDev.util.tests.modelGenerated.RdTestSession
import com.intellij.remoteDev.util.tests.modelGenerated.RdTestSessionException
import com.intellij.remoteDev.util.tests.modelGenerated.RdTestSessionExceptionCause
import com.intellij.remoteDev.util.tests.modelGenerated.RdTestSessionStackTraceElement
import com.intellij.util.ui.UIUtil

internal class AgentTestLogger(logger: Logger, private val factory: AgentTestLoggerFactory) : DelegatingLogger<Logger>(logger) {
  override fun error(message: String?, t: Throwable?, vararg details: String) {
    super.error(message, t, *details)
    factory.testSession.get()?.let {
      val messageToTheTestRunner =
        if (details.isNotEmpty()) message + "\n Details: \n" + details.joinToString("\n")
        else message
      sendToTestRunner(it, messageToTheTestRunner, t)
    }
  }

  private fun sendToTestRunner(session: RdTestSession, message: String?, t: Throwable?) {
    fun getRdStackTrace(_stackTrace: Array<StackTraceElement>?): List<RdTestSessionStackTraceElement> =
      _stackTrace?.map { it -> RdTestSessionStackTraceElement(it.className, it.methodName, it.fileName.orEmpty(), it.lineNumber) }
      ?: emptyList()

    val rdtseMessage: String =
      if (message != t?.message)
        listOfNotNull(message, t?.message).joinToString(": ")
      else message ?: "There was an error of type ${t?.javaClass?.name}"
    val rdtseType = t?.javaClass?.typeName ?: "<LOG_ERROR>"
    val rdtseStackTrace = getRdStackTrace(t?.stackTrace)
    val rdtseCause = t?.cause?.let { cause ->
      RdTestSessionExceptionCause(cause.javaClass.typeName, cause.message, getRdStackTrace(cause.stackTrace))
    }

    val rdTestSessionException = RdTestSessionException(rdtseType, rdtseMessage, rdtseStackTrace, rdtseCause)

    info("Scheduling firing ex to the test runner ${rdTestSessionException.message}")
    UIUtil.invokeLaterIfNeeded {
      info("Fired ex to the test runner ${rdTestSessionException.message}")
      session.sendException.fire(rdTestSessionException)
    }
  }
}
