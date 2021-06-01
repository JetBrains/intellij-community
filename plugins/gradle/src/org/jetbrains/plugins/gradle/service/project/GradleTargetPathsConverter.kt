// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware.Companion.getEnvironmentConfigurationProvider
import org.jetbrains.plugins.gradle.model.ProjectImportAction.AllModels
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.ReflectionTraverser
import org.jetbrains.plugins.gradle.util.ReflectionTraverser.Visitor
import java.io.Closeable
import java.io.File

internal class GradleTargetPathsConverter(private val executionSettings: GradleExecutionSettings) : Closeable {
  private val traverser = ReflectionTraverser()

  fun mayBeApplyTo(allModels: AllModels) {
    val pathMapper = executionSettings.getEnvironmentConfigurationProvider()?.pathMapper ?: return
    allModels.applyPathsConverter {
      traverser.walk(it!!, object : Visitor {
        override fun process(instance: Any) {
          if (instance !is File) return
          val remotePath = instance.path
          if (!pathMapper.canReplaceRemote(remotePath)) return
          val localPath = pathMapper.convertToLocal(remotePath)
          try {
            val field = File::class.java.getDeclaredField("path")
            field.isAccessible = true
            field[instance] = localPath
          }
          catch (ignore: Throwable) {
          }
        }
      })
    }
  }

  override fun close() {
    traverser.close()
  }
}