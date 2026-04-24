// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectModel.mock

import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.DefaultGradleSourceSetModel
import org.jetbrains.plugins.gradle.model.DefaultExternalProject
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceSet
import org.jetbrains.plugins.gradle.model.ExternalProject

internal class GradleTestExternalProject {

  var numHolderModules: Int = 1
  var numSourceSetModules: Int = 1

  companion object {

    fun testExternalProjects(configure: (GradleTestExternalProject) -> Unit): List<ExternalProject> {
      val configuration = GradleTestExternalProject()
      configure(configuration)
      return buildList {
        repeat(configuration.numHolderModules) { holderModuleIndex ->
          val sourceSets = buildMap {
            repeat(configuration.numSourceSetModules) { sourceSetIndex ->
              val sourceSet = DefaultExternalSourceSet().also {
                it.name = "source-set-$sourceSetIndex"
                it.compilerArguments = emptyList()
              }
              put(sourceSet.name, sourceSet)
            }
          }
          val sourceSetModel = DefaultGradleSourceSetModel().also {
            it.sourceSets = sourceSets
          }
          val projectModel = DefaultExternalProject().also {
            it.name = "module-$holderModuleIndex"
            it.identityPath = ":module-$holderModuleIndex"
            it.sourceSetModel = sourceSetModel
          }
          add(projectModel)
        }
      }
    }
  }
}
