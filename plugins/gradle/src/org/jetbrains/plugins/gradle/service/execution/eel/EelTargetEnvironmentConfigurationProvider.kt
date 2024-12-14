// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.eel

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.eel.EelTargetEnvironmentRequest
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.toNioPath
import com.intellij.util.PathMapper

class EelTargetEnvironmentConfigurationProvider(val eel: EelApi) : TargetEnvironmentConfigurationProvider {

  override val environmentConfiguration: TargetEnvironmentConfiguration by lazy { resolveEnvironmentConfiguration() }
  override val pathMapper: PathMapper by lazy { EelPathMapper(eel) }

  private fun resolveEnvironmentConfiguration(): TargetEnvironmentConfiguration {
    return EelTargetEnvironmentRequest.Configuration(eel)
  }

  private class EelPathMapper(private val eel: EelApi) : PathMapper {

    override fun isEmpty(): Boolean = false

    override fun canReplaceLocal(localPath: String): Boolean {
      val nio = localPath.toNioPathOrNull() ?: return false
      return eel.mapper.getOriginalPath(nio) != null
    }

    override fun convertToLocal(remotePath: String): String {
      val nio = remotePath.toNioPathOrNull() ?: throw IllegalArgumentException("Unable to map path $remotePath")
      val eelPath = eel.fs.getPath(nio.toCanonicalPath())
      return eelPath.toNioPath(eel).toCanonicalPath()
    }

    override fun canReplaceRemote(remotePath: String): Boolean {
      return true
    }

    override fun convertToRemote(localPath: String): String {
      val nioPath = localPath.toNioPathOrNull() ?: throw IllegalArgumentException("Unable to map path $localPath")
      return eel.mapper.getOriginalPath(nioPath).toString()
    }

    override fun convertToRemote(paths: MutableCollection<String>): List<String> {
      return paths.map {
        convertToRemote(it)
      }
    }
  }
}