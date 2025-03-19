package com.intellij.remoteDev.tests.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.remoteDev.tests.modelGenerated.RdTestSession
import com.jetbrains.rd.util.lifetime.Lifetime

object LogFactoryHandler {
  inline fun <reified T : AgentTestLoggerFactory> bindSession(lifetime: Lifetime, session: RdTestSession) {
    val logger = logger<T>()

    val ideaLoggerFactory = Logger.getFactory()
    val agentTestLoggerFactory = if (ideaLoggerFactory is T) {
      logger.warn(T::class.simpleName + " is already registered")
      ideaLoggerFactory
    }
    else {
      val constructor = T::class.java.constructors.find { it.parameters.map { it.type } == listOf(Logger.Factory::class.java) }
                        ?: error("Should have found a constructor")
      val agentTestLoggerFactory = constructor.newInstance(ideaLoggerFactory) as T
      Logger.setFactory(agentTestLoggerFactory)
      agentTestLoggerFactory
    }

    agentTestLoggerFactory.testSession.set(session)
    lifetime.onTermination { agentTestLoggerFactory.testSession.set(null) }
  }

  inline fun <reified T : AgentTestLoggerFactory> assertLoggerFactory() {
    assert(Logger.getFactory()::class.java == T::class.java) {
      "Logger Factory was overridden during test method execution. " +
      "Inspect logs to find stack trace of the overrider. " +
      "Overriding logger factory leads to breaking distributes test log processing."
    }
  }
}