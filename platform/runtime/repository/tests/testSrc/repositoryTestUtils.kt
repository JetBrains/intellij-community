// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository

import com.intellij.platform.runtime.repository.impl.RuntimeModuleRepositoryImpl
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.createParentDirectories
import org.intellij.lang.annotations.Language
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.writeText

internal fun DirectoryContentBuilder.xml(name: String, @Language("XML") content: String) {
  file(name, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n$content")
}

internal fun createRepository(basePath: Path, vararg descriptors: RawRuntimeModuleDescriptor, bootstrapModuleName: String? = null): RuntimeModuleRepository {
  val moduleDescriptorsJarPath = basePath.resolve("module-descriptors.jar")
  RuntimeModuleRepositorySerialization.saveToJar(descriptors.asList(), bootstrapModuleName, moduleDescriptorsJarPath, 0)
  return RuntimeModuleRepositoryImpl(moduleDescriptorsJarPath)
}

internal fun writePluginXml(resourceRoot: Path, @Language("XM") content: String) {
  val path = resourceRoot / "META-INF" / "plugin.xml"
  path.createParentDirectories()
  path.writeText(content)
}