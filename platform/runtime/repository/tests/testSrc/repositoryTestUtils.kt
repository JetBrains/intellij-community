// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository

import com.intellij.openapi.util.io.NioFiles.createParentDirectories
import com.intellij.platform.runtime.repository.impl.RuntimeModuleRepositoryImpl
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.util.io.DirectoryContentBuilder
import org.intellij.lang.annotations.Language
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.writeText

fun DirectoryContentBuilder.xml(name: String, @Language("XML") content: String) {
  file(name, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n$content")
}

fun createRepository(basePath: Path, vararg descriptors: RawRuntimeModuleDescriptor): RuntimeModuleRepository {
  val moduleDescriptorsPath = basePath.resolve("module-descriptors.dat")
  return RuntimeModuleRepositoryImpl(moduleDescriptorsPath,
                                     RawRuntimeModuleRepositoryData.create(descriptors.associateBy { it.moduleId }, emptyList(), basePath))
}

fun createModuleDescriptor(id: String, resourcePaths: List<String>, dependencies: List<String> = emptyList()): RawRuntimeModuleDescriptor {
  return RawRuntimeModuleDescriptor.create(RuntimeModuleId.raw(id), resourcePaths, dependencies.map { RuntimeModuleId.raw(it) })
}

fun writePluginXml(resourceRoot: Path, @Language("XM") content: String) {
  val path = resourceRoot / "META-INF" / "plugin.xml"
  createParentDirectories(path)
  path.writeText(content)
}