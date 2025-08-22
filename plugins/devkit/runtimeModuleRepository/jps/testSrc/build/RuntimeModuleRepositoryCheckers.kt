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
  val actualIds = buildRepositoryData.allIds.filter { it != RUNTIME_REPOSITORY_MARKER_MODULE && it != "${RUNTIME_REPOSITORY_MARKER_MODULE}${RuntimeModuleId.TESTS_NAME_SUFFIX}" }
  val builder = RawDescriptorListBuilder()
  builder.expected()
  JpsBuildTestCase.assertSameElements(actualIds, builder.descriptors.map { it.id })
  for (expectedDescriptor in builder.descriptors) {
    JpsBuildTestCase.assertEquals("Different data for '${expectedDescriptor.id}'.", expectedDescriptor, buildRepositoryData.findDescriptor(expectedDescriptor.id)!!)
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
    if (RuntimeModuleRepositoryGenerator.GENERATE_DESCRIPTORS_FOR_TEST_MODULES) {
      descriptor(id, listOf("test/$resourceDirName"), dependencies.asList())
    }
  }

  fun descriptor(id: String, resources: List<String>, dependencies: List<String>) {
    descriptors.add(RawRuntimeModuleDescriptor.create(id, resources, dependencies))
  }
}

const val RUNTIME_REPOSITORY_MARKER_MODULE: String = "intellij.idea.community.main"