// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon

import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.client.DaemonStopClient
import org.gradle.launcher.daemon.context.DaemonConnectDetails
import org.gradle.launcher.daemon.protocol.Command
import org.gradle.launcher.daemon.protocol.StopWhenIdle
import org.gradle.launcher.daemon.registry.DaemonRegistry

class DaemonStopWhenIdleAction(serviceDirectoryPath: String?) : AbstractDaemonStopAction(serviceDirectoryPath) {

  override val commandClass: Class<out Command>
    get() = StopWhenIdle::class.java

  override fun stopAll(stopClient: DaemonStopClient, daemonServices: ServiceRegistry) {
    val daemonRegistry = daemonServices.get(DaemonRegistry::class.java)
    val daemonInfos = ArrayList<DaemonConnectDetails>(daemonRegistry.all)
    stopClient.gracefulStop(daemonInfos)
  }
}
