// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware.Companion.getEnvironmentConfigurationProvider
import org.jetbrains.plugins.gradle.model.ProjectImportAction.AllModels
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.ReflectionTraverser
import java.io.Closeable
import java.io.File

internal class GradleTargetPathsConverter(private val executionSettings: GradleExecutionSettings) : Closeable {
  private val traverser = ReflectionTraverser()

  fun mayBeApplyTo(allModels: AllModels) {
    val pathMapper = executionSettings.getEnvironmentConfigurationProvider()?.pathMapper ?: return
    allModels.applyPathsConverter { rootObject ->
      traverser.walk(rootObject!!, listOf(String::class.java), listOf(File::class.java)) {
        if (it !is File) return@walk
        val remotePath = it.path
        if (!pathMapper.canReplaceRemote(remotePath)) return@walk
        val localPath = pathMapper.convertToLocal(remotePath)
        try {
          val field = File::class.java.getDeclaredField("path")
          field.isAccessible = true
          field[it] = localPath
        }
        catch (reflectionError: Throwable) {
          LOG.error("Failed to update mapped file", reflectionError)
        }
      }
    }
  }

  override fun close() {
    traverser.close()
  }

  companion object {
    private val LOG = logger<GradleTargetPathsConverter>()
  }
}