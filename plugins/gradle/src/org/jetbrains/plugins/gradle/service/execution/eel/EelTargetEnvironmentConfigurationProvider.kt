// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.eel

import com.intellij.execution.target.EelTargetEnvironmentRequest
import com.intellij.execution.target.HostPort
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelTunnelsApi.HostAddress
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.forwardLocalPort
import com.intellij.util.PathMapper
import com.intellij.util.net.NetUtils
import org.jetbrains.plugins.gradle.GradleCoroutineScope.gradleCoroutineScope
import org.jetbrains.plugins.gradle.service.execution.GradleServerConfigurationProvider
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class EelTargetEnvironmentConfigurationProvider(val eel: EelApi, val project: Project) : GradleServerConfigurationProvider {

  override val environmentConfiguration: TargetEnvironmentConfiguration by lazy { resolveEnvironmentConfiguration() }
  override val pathMapper: PathMapper by lazy { EelPathMapper(eel, project) }

  private fun resolveEnvironmentConfiguration(): TargetEnvironmentConfiguration {
    return EelTargetEnvironmentRequest.Configuration(eel)
  }

  private class EelPathMapper(private val eel: EelApi, private val project: Project) : PathMapper {

    override fun isEmpty(): Boolean = false

    override fun canReplaceLocal(localPath: String): Boolean {
      val nio = Path.of(localPath)
      return nio.asEelPath().descriptor != LocalEelDescriptor
    }

    override fun convertToLocal(remotePath: String): String {
      val nio = Path.of(remotePath)
      val eelPath = eel.fs.getPath(nio.toCanonicalPath())
      return eelPath.asNioPath().toCanonicalPath()
    }

    override fun canReplaceRemote(remotePath: String): Boolean {
      return true
    }

    override fun convertToRemote(localPath: String): String {
      val nioPath = Path.of(localPath)
      return nioPath.asEelPath().toString()
    }

    override fun convertToRemote(paths: MutableCollection<String>): List<String> {
      return paths.map {
        convertToRemote(it)
      }
    }
  }

  override fun getClientCommunicationAddress(
    taskId: ExternalSystemTaskId?,
    targetEnvironmentConfiguration: TargetEnvironmentConfiguration,
    gradleServerHostPort: HostPort,
  ): HostPort {
    val localPort = if (taskId == null) {
      runBlockingCancellable {
        forwardToolingProxyPortOntoLocalMachine(gradleServerHostPort.port)
      }
    }
    else {
      GradleEelProxyManager.getInstance(project).launchProxy(taskId, gradleServerHostPort.port)
    }
    return HostPort(NetUtils.getLocalHostString(), localPort)
  }

  private suspend fun forwardToolingProxyPortOntoLocalMachine(port: Int): Int {
    val eel = project.getEelDescriptor().toEelApi()
    // the address to which all the request will be forwarded to
    // in this case port -- port of the tooling proxy
    val address = HostAddress.Builder(port.toUShort())
      .connectionTimeout(90.seconds)
      .build()
    // the local port which are bridges requests into [port]
    val localPort = NetUtils.findAvailableSocketPort()
    project.gradleCoroutineScope.forwardLocalPort(eel.tunnels, localPort, address)
    return localPort
  }
}