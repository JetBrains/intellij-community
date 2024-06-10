// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DaemonServicesFactory")

package org.jetbrains.plugins.gradle.internal.daemon

import org.gradle.initialization.BuildLayoutParameters
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.client.DaemonClientFactory
import org.gradle.launcher.daemon.configuration.DaemonParameters
import java.io.ByteArrayInputStream
import java.io.File

fun getDaemonServiceFactory(daemonClientFactory: DaemonClientFactory, myServiceDirectoryPath: String?): ServiceRegistry {
  val layoutParameters = getBuildLayoutParameters(myServiceDirectoryPath)
  val daemonParameters = getDaemonParameters(layoutParameters)
  return getDaemonServices(daemonClientFactory, daemonParameters)
}

private fun getDaemonServices(daemonClientFactory: DaemonClientFactory, parameters: DaemonParameters): ServiceRegistry {
  return daemonClientFactory.createBuildClientServices(
    { },
    parameters,
    ByteArrayInputStream(ByteArray(0))
  )
}

private fun getBuildLayoutParameters(myServiceDirectoryPath: String?): BuildLayoutParameters {
  val layout = BuildLayoutParameters()
  if (!myServiceDirectoryPath.isNullOrEmpty()) {
    layout.setGradleUserHomeDir(File(myServiceDirectoryPath))
  }
  return layout
}
