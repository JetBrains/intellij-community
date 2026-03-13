// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.jetbrains.jps.builders.JpsBuildTestCase
import java.nio.file.Path

fun checkRuntimeModuleRepository(outputDir: Path,
                                 expected: RawDescriptorListBuilder.() -> Unit) {
  val jarPath = outputDir.resolve(RuntimeModuleRepositoryGenerator.JAR_REPOSITORY_FILE_NAME)
  checkRuntimeModuleRepository(RuntimeModuleRepositorySerialization.loadFromJar(jarPath), expected)
  val compactPath = outputDir.resolve(RuntimeModuleRepositoryGenerator.COMPACT_REPOSITORY_FILE_NAME)
  checkRuntimeModuleRepository(RuntimeModuleRepositorySerialization.loadFromCompactFile(compactPath), expected)
}

private fun checkRuntimeModuleRepository(
  buildRepositoryData: RawRuntimeModuleRepositoryData,
  expected: RawDescriptorListBuilder.() -> Unit,
) {
  val actualIds = buildRepositoryData.allModuleIds.filter { it != RUNTIME_REPOSITORY_MARKER_MODULE && it != RUNTIME_REPOSITORY_TESTS_MARKER_MODULE }
  val builder = RawDescriptorListBuilder()
  builder.expected()
  JpsBuildTestCase.assertSameElements(actualIds, builder.descriptors.map { it.moduleId })
  for (expectedDescriptor in builder.descriptors) {
    JpsBuildTestCase.assertEquals("Different data for '${expectedDescriptor.moduleId.presentableName}'.", expectedDescriptor, buildRepositoryData.findDescriptor(expectedDescriptor.moduleId)!!)
  }
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

internal val RUNTIME_REPOSITORY_MARKER_MODULE: RuntimeModuleId = RuntimeModuleId.module("intellij.idea.community.main")
internal val RUNTIME_REPOSITORY_TESTS_MARKER_MODULE: RuntimeModuleId = RuntimeModuleId.moduleTests("intellij.idea.community.main")