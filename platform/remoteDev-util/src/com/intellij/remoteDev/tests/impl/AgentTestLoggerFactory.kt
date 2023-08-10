package com.intellij.remoteDev.tests.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.remoteDev.tests.modelGenerated.RdTestSession
import java.util.concurrent.atomic.AtomicReference


open class AgentTestLoggerFactory(private val loggerFactory: Logger.Factory) : Logger.Factory {

  val testSession = AtomicReference<RdTestSession?>(null)

  override fun getLoggerInstance(category: String): Logger {
    return AgentTestLogger(loggerFactory.getLoggerInstance(category), this)
  }
}


