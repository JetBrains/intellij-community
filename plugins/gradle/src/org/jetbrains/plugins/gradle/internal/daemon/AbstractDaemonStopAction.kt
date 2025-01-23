// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.diagnostic.logger
import org.gradle.internal.id.IdGenerator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.client.DaemonClientFactory
import org.gradle.launcher.daemon.client.DaemonConnector
import org.gradle.launcher.daemon.client.DaemonStopClient
import org.gradle.launcher.daemon.client.DaemonStopClientExecuter
import org.gradle.launcher.daemon.protocol.Command
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.registry.DaemonRegistry
import java.io.File

private val LOG = logger<DaemonStopAction>()

abstract class AbstractDaemonStopAction(private val serviceDirectoryPath: String?) : DaemonAction(serviceDirectoryPath) {

  protected abstract val commandClass: Class<out Command>

  protected abstract fun stopAll(stopClient: DaemonStopClient, daemonServices: ServiceRegistry)

  fun run(daemonClientFactory: DaemonClientFactory) {
    val daemonServices = getDaemonServices(daemonClientFactory)
    if (GradleVersionUtil.isCurrentGradleAtLeast("8.12")) {
      if (serviceDirectoryPath == null) {
        LOG.info("Gradle daemon service directory path is not defined. Unable to execute a daemon action.")
        return
      }
      val executor = daemonServices.get(DaemonStopClientExecuter::class.java)
      executor.execute(daemonServices, File(serviceDirectoryPath)) { stopClient ->
        stopAll(stopClient, daemonServices)
      }
    }
    else {
      val stopClient = daemonServices.get(DaemonStopClient::class.java)
      stopAll(stopClient, daemonServices)
    }
  }

  fun run(daemonClientFactory: DaemonClientFactory, tokens: List<ByteArray>) {
    val daemonServices = getDaemonServices(daemonClientFactory)
    val daemonRegistry = daemonServices.get(DaemonRegistry::class.java)
    val daemonConnector = daemonServices.get(DaemonConnector::class.java)
    val idGenerator = daemonServices.get(IdGenerator::class.java)

    val daemons: MutableList<DaemonInfo> = ArrayList(daemonRegistry.all)
    tokens.forEach { token ->
      val daemonIterator = daemons.iterator()
      while (daemonIterator.hasNext()) {
        val info = daemonIterator.next()
        if (!info.token.contentEquals(token)) {
          continue
        }
        daemonIterator.remove()
        val connection = daemonConnector.maybeConnect(info)
        if (connection != null) {
          try {
            val stopCommand = createCommand(commandClass, idGenerator.generateId(), token)
            connection.dispatch(stopCommand)
          }
          finally {
            connection.stop()
          }
        }
        break
      }
    }
  }
}
