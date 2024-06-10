// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.internal.daemon

import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.client.DaemonClientFactory
import org.gradle.launcher.daemon.protocol.Command

/**
 * @author Vladislav.Soroka
 */
abstract class DaemonAction(private val myServiceDirectoryPath: String?) {

  protected fun getDaemonServices(daemonClientFactory: DaemonClientFactory): ServiceRegistry {
    return getDaemonServiceFactory(daemonClientFactory, myServiceDirectoryPath)
  }

  companion object {
    @JvmStatic
    fun <T : Command> createCommand(commandClass: Class<T>, id: Any, token: ByteArray?): T {
      try {
        return commandClass.constructors[0].newInstance(id, token) as T
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
    }
  }
}
