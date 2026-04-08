// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.generator.tests

import com.intellij.devkit.runtimeModuleRepository.generator.ContentModuleDetector
import com.intellij.devkit.runtimeModuleRepository.generator.ContentModuleRegistrationData
import com.intellij.devkit.runtimeModuleRepository.generator.JpsCompilationResourcePathsSchema
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryValidator
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleId.DEFAULT_NAMESPACE
import com.intellij.platform.runtime.repository.RuntimeModuleId.raw
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.platform.runtime.repository.serialization.RawRuntimePluginHeader
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
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
  val dummyContentModuleDetector = object : ContentModuleDetector {
    override fun findContentModuleData(jpsModule: JpsModule): ContentModuleRegistrationData {
      return ContentModuleRegistrationData(name = jpsModule.name,
                                           namespace = RuntimeModuleId.DEFAULT_NAMESPACE,
                                           visibility = RuntimeModuleVisibility.PUBLIC)
    }
  }
  val generatedDescriptors =
    RuntimeModuleRepositoryGenerator.generateRuntimeModuleDescriptorsForWholeProject(project,
                                                                                     resourcePathsSchema,
                                                                                     dummyContentModuleDetector)
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

  fun testDescriptor(moduleName: String,
                     vararg dependencies: String,
                     resourceDirName: String = moduleName.removeSuffix(RuntimeModuleId.TESTS_NAME_SUFFIX)) {
    descriptor(raw(moduleName, RuntimeModuleId.LEGACY_JPS_MODULE_TESTS_NAMESPACE), listOf("test/$resourceDirName"),
               dependencies.asList().map { raw(it, DEFAULT_NAMESPACE) })
  }

  fun testDescriptor(moduleName: String,
                     vararg dependencies: RuntimeModuleId,
                     resourceDirName: String = moduleName.removeSuffix(RuntimeModuleId.TESTS_NAME_SUFFIX)) {
    descriptor(raw(moduleName, RuntimeModuleId.LEGACY_JPS_MODULE_TESTS_NAMESPACE), listOf("test/$resourceDirName"),
               dependencies.asList())
  }

  fun descriptor(id: String, resources: List<String>, dependencies: List<String>) {
    descriptor(RuntimeModuleId.raw(id, RuntimeModuleId.DEFAULT_NAMESPACE), resources,
               dependencies.map { RuntimeModuleId.raw(it, RuntimeModuleId.DEFAULT_NAMESPACE) })
  }

  fun descriptor(id: RuntimeModuleId, resources: List<String>, dependencies: List<RuntimeModuleId>) {
    descriptors.add(RawRuntimeModuleDescriptor.create(id, resources,
                                                      dependencies))
  }
}