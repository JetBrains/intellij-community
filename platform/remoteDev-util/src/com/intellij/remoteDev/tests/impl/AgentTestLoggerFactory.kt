package com.intellij.remoteDev.tests.impl

import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.remoteDev.tests.isDistributedTestMode
import com.intellij.remoteDev.tests.modelGenerated.RdTestSession
import com.jetbrains.rd.util.lifetime.Lifetime
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

internal class AgentTestLoggerFactory(private val loggerFactory: Logger.Factory) : Logger.Factory {
  companion object {
    fun bindSession(lifetime: Lifetime, session: RdTestSession) {
      val factory = Logger.getFactory() as AgentTestLoggerFactory
      factory.testSession.set(session)
      lifetime.onTermination { factory.testSession.set(null) }
    }
  }

  val testSession = AtomicReference<RdTestSession?>(null)

  override fun getLoggerInstance(category: String): Logger {
    return AgentTestLogger(loggerFactory.getLoggerInstance(category), this)
  }

  @InternalIgnoreDependencyViolation
  internal class MyApplicationListener: ApplicationLoadListener {
    override fun beforeApplicationLoaded(application: Application, configPath: Path) {
      if (!application.isDistributedTestMode) {
        return
      }

      val logger = logger<MyApplicationListener>()
      logger.info("Installing ${AgentTestLoggerFactory::class.simpleName}")

      val ideaLoggerFactory = Logger.getFactory()
      if (ideaLoggerFactory is AgentTestLoggerFactory) {
        logger.error("AgentTestLoggerFactory is already registered")
        return
      }

      Logger.setFactory(AgentTestLoggerFactory(ideaLoggerFactory))
    }
  }
}


