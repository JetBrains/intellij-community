// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.internal.daemon

import org.gradle.initialization.BuildLayoutParameters
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.client.DaemonClientFactory
import org.gradle.launcher.daemon.protocol.Command
import java.io.ByteArrayInputStream
import java.io.File

/**
 * @author Vladislav.Soroka
 */
abstract class DaemonAction(private val myServiceDirectoryPath: String?) {

  protected fun getDaemonServices(daemonClientFactory: DaemonClientFactory): ServiceRegistry {
    val layout = BuildLayoutParameters()
    if (!myServiceDirectoryPath.isNullOrEmpty()) {
      layout.setGradleUserHomeDir(File(myServiceDirectoryPath))
    }
    val daemonParameters = getDaemonParameters(layout)
    return daemonClientFactory.createBuildClientServices(
      { },
      daemonParameters,
      ByteArrayInputStream(ByteArray(0))
    )
  }

  companion object {
    @JvmStatic
    protected fun <T: Command> createCommand(commandClass: Class<T>, id: Any, token: ByteArray?): T {
      try {
        return commandClass.constructors[0].newInstance(id, token) as T
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
    }
  }
}
