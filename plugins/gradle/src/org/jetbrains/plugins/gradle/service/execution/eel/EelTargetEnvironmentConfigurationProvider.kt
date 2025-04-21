// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.eel

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.eel.EelTargetEnvironmentRequest
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.asNioPathOrNull
import com.intellij.util.PathMapper
import java.nio.file.Path

class EelTargetEnvironmentConfigurationProvider(val eel: EelApi, val project: Project) : TargetEnvironmentConfigurationProvider {

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
      return eelPath.asNioPathOrNull(project)!!.toCanonicalPath()
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
}