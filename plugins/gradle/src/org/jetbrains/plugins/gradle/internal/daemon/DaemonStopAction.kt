// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon

import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.client.DaemonStopClient
import org.gradle.launcher.daemon.protocol.Command
import org.gradle.launcher.daemon.protocol.Stop

/**
 * @author Vladislav.Soroka
 */
class DaemonStopAction(serviceDirectoryPath: String?) : AbstractDaemonStopAction(serviceDirectoryPath) {

  override val commandClass: Class<out Command>
    get() = Stop::class.java

  override fun stopAll(stopClient: DaemonStopClient, daemonServices: ServiceRegistry) {
    stopClient.stop()
  }
}
