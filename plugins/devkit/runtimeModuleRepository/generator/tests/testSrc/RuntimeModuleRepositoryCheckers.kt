// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.generator.tests

import com.intellij.devkit.runtimeModuleRepository.generator.JpsCompilationResourcePathsSchema
import com.intellij.devkit.runtimeModuleRepository.generator.NoContentModuleDetector
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryValidator
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.platform.runtime.repository.serialization.RawRuntimePluginHeader
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.jps.model.JpsProject
import java.nio.file.Path

private fun checkRuntimeModuleRepository(
  buildRepositoryData: RawRuntimeModuleRepositoryData,
  expected: RawDescriptorListBuilder.() -> Unit,
) {
  val builder = RawDescriptorListBuilder()
  builder.expected()
  UsefulTestCase.assertSameElements(buildRepositoryData.allModuleIds, builder.descriptors.map { it.moduleId })
  for (expectedDescriptor in builder.descriptors) {
    UsefulTestCase.assertEquals("Different data for '${expectedDescriptor.moduleId.presentableName}'.", expectedDescriptor, buildRepositoryData.findDescriptor(expectedDescriptor.moduleId)!!)
  }
}

internal fun generateAndCheck(project: JpsProject, basePath: Path, expected: RawDescriptorListBuilder.() -> Unit) {
  val generatedDescriptors = generateAndValidateRuntimeModuleRepository(project)
  val moduleDescriptors = generatedDescriptors.associateBy { it.moduleId }
  val pluginHeaders = emptyList<RawRuntimePluginHeader>()
  val rawData = RawRuntimeModuleRepositoryData.create(moduleDescriptors, pluginHeaders, basePath)
  checkRuntimeModuleRepository(rawData, expected)
}

internal fun generateAndValidateRuntimeModuleRepository(project: JpsProject): List<RawRuntimeModuleDescriptor> {
  val resourcePathsSchema = JpsCompilationResourcePathsSchema(project)
  val generatedDescriptors =
    RuntimeModuleRepositoryGenerator.generateRuntimeModuleDescriptorsForWholeProject(project,
                                                                                     resourcePathsSchema,
                                                                                     NoContentModuleDetector)
  validate(generatedDescriptors)
  return generatedDescriptors
}

private fun validate(descriptors: List<RawRuntimeModuleDescriptor>) {
  RuntimeModuleRepositoryValidator.validate(descriptors,  object : RuntimeModuleRepositoryValidator.ErrorReporter {
    override fun reportDuplicatingId(moduleId: RuntimeModuleId) {
      error("Duplicating module id: $moduleId")
    }
  })
}

class RawDescriptorListBuilder {
  val descriptors = ArrayList<RawRuntimeModuleDescriptor>()

  fun descriptor(id: String, vararg dependencies: String, resourceDirName: String? = id) {
    val resources = if (resourceDirName != null) listOf("production/$resourceDirName") else emptyList()
    descriptor(id, resources, dependencies.asList())
  }

  fun testDescriptor(id: String,
                     vararg dependencies: String,
                     resourceDirName: String = id.removeSuffix(RuntimeModuleId.TESTS_NAME_SUFFIX)) {
    descriptor(id, listOf("test/$resourceDirName"), dependencies.asList())
  }

  fun descriptor(id: String, resources: List<String>, dependencies: List<String>) {
    descriptors.add(RawRuntimeModuleDescriptor.create(RuntimeModuleId.raw(id), resources, dependencies.map { RuntimeModuleId.raw(it) }))
  }
}