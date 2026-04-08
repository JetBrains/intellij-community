// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.runtimeModuleRepository

import com.intellij.devkit.runtimeModuleRepository.generator.ContentModuleDetector
import com.intellij.devkit.runtimeModuleRepository.generator.ContentModuleRegistrationData
import com.intellij.devkit.runtimeModuleRepository.generator.ResourcePathsSchema
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator
import com.intellij.devkit.runtimeModuleRepository.generator.isProjectLevel
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import com.intellij.platform.runtime.repository.impl.RuntimeModuleRepositoryImpl
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.platform.testFramework.monorepo.productionOutputPaths
import com.intellij.platform.testFramework.monorepo.testOutputPaths
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Generates a runtime module repository for the whole project without saving it on disk.
 */
fun generateRuntimeModuleRepositoryForTests(monorepoProject: JpsProject): RuntimeModuleRepository {
  val allProjectLibraries = JpsJavaExtensionService.dependencies(monorepoProject).productionOnly().runtimeOnly().libraries.filter { it.isProjectLevel }
  val moduleDescriptors = RuntimeModuleRepositoryGenerator.generateRuntimeModuleDescriptors(
    includedProduction = monorepoProject.modules,
    includedTests = emptyList(),
    includedProjectLibraries = allProjectLibraries,
    resourcePathsSchema = ResourcePathsSchemaForTests,
    contentModuleDetector = ContentModuleDetectorInSourceCode(),
  )
  
  //the repository won't be saved on disk so the actual location of the directory doesn't matter much
  val outputDirectory = JpsModelSerializationDataService.getBaseDirectoryPath(monorepoProject)!!.resolve("out/module-descriptors-for-tests")
  val repositoryData = RawRuntimeModuleRepositoryData.create(moduleDescriptors.associateBy { it.moduleId }, emptyList(), outputDirectory)
  return RuntimeModuleRepositoryImpl(outputDirectory.resolve(RuntimeModuleRepositoryGenerator.COMPACT_REPOSITORY_FILE_NAME), repositoryData)
}

class ContentModuleDetectorInSourceCode : ContentModuleDetector {
  private val hasContentModuleDescriptor = HashMap<JpsModule, Boolean>()

  override fun findContentModuleData(jpsModule: JpsModule): ContentModuleRegistrationData? {
    val hasDescriptor = hasContentModuleDescriptor.getOrPut(jpsModule) {
      JpsJavaExtensionService.getInstance().findSourceFileInProductionRoots(jpsModule, "${jpsModule.name}.xml") != null
    }
    if (!hasDescriptor) return null
    return ContentModuleRegistrationData(jpsModule.name, "jetbrains", RuntimeModuleVisibility.PUBLIC)
  }
}

private object ResourcePathsSchemaForTests : ResourcePathsSchema {
  override fun moduleOutputPaths(module: JpsModule): List<String> {
    return module.productionOutputPaths.map { it.invariantSeparatorsPathString }
  }

  override fun moduleTestOutputPaths(module: JpsModule): List<String> {
    return module.testOutputPaths.map { it.invariantSeparatorsPathString }
  }

  override fun libraryPaths(library: JpsLibrary): List<String> {
    return library.getPaths(JpsOrderRootType.COMPILED).map { it.invariantSeparatorsPathString }
  }
}