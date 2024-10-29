// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DAModule
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerManager
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerViewImpl
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/** The command performs the same action as
 * Go to Maven/Gradle tool window > select name of module > hit "Analyze dependencies"
 * */
class AnalyzeDependenciesCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "analyzeDependencies"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val (moduleName, providerId) = extractCommandList(PREFIX, " ")
    withContext(Dispatchers.EDT) {
      val module = ModuleManager.getInstance(project).modules.firstOrNull { it.name == moduleName } ?: throw IllegalArgumentException(
        "Module $moduleName not found")
      val systemId = ProjectSystemId.findById(providerId) ?: throw IllegalArgumentException("Provider $providerId not found")
      val dependencyAnalyzerManager = DependencyAnalyzerManager.getInstance(project)
      val dependencyAnalyzerView = writeIntentReadAction { dependencyAnalyzerManager.getOrCreate(systemId) as DependencyAnalyzerViewImpl }
      dependencyAnalyzerView.setSelectedDependency(module, DAModule(moduleName))
      withTimeout(20.seconds) {
        while (dependencyAnalyzerView.getDependencies().isEmpty()) {
          delay(1000)
        }
      }
      val expected = (ModuleRootManager.getInstance(module) as ModuleRootComponentBridge)
        .storage
        .entities(LibraryEntity::class.java)
        // Gradle: org.junit.jupiter:junit-jupiter:5.9.1 -> org.junit.jupiter:junit-jupiter:5.9.1
        // Maven: org.junit.jupiter:junit-jupiter:5.9.1 -> org.junit.jupiter:junit-jupiter:5.9.1
        .map { it.name.replace("Gradle: ", "").replace("Maven: ", "") }.toList()
      val actual = dependencyAnalyzerView.getDependencies()
        .map { it.data.toString() }
        .toSet()

      if (!actual.containsAll(expected)) {
        throw IllegalStateException("Expected dependencies: ${expected}, actual: $actual")
      }
    }
  }

  override fun getName(): String {
    return NAME
  }
}