// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

import org.jetbrains.jps.builders.CompileScopeTestBuilder
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.impl.JpsProjectSerializationDataExtensionImpl
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

abstract class RuntimeModuleRepositoryTestCase : JpsBuildTestCase() {
  override fun setUp() {
    super.setUp()
    addModule(RUNTIME_REPOSITORY_MARKER_MODULE)
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myProject).outputUrl = getUrl("out")
    val projectDir = Path(getAbsolutePath("project"))
    val modulesXml = projectDir.resolve(".idea/modules.xml")
    modulesXml.createParentDirectories()
    modulesXml.writeText("""
       |<?xml version="1.0" encoding="UTF-8"?>
       |<project version="4">
       |  <component name="ProjectModuleManager" />
       |</project>      
    """.trimMargin())
    myProject.container.setChild(JpsProjectSerializationDataExtensionImpl.ROLE, JpsProjectSerializationDataExtensionImpl(projectDir))
  }

  protected fun addModule(name: String, vararg dependencies: JpsModule, withTests: Boolean, withSources: Boolean = true): JpsModule {
    val module = addModule(name, emptyArray(), null, null, jdk)
    if (withSources) {
      module.addSourceRoot(getUrl("$name/src"), JavaSourceRootType.SOURCE)
    }
    if (withTests) {
      module.addSourceRoot(getUrl("$name/testSrc"), JavaSourceRootType.TEST_SOURCE)
    }
    for (dependency in dependencies) {
      module.dependenciesList.addModuleDependency(dependency)
    }
    return module
  }

  protected fun checkModuleRepository(expected: RawDescriptorListBuilder.() -> Unit) {
    val outputDir = orCreateProjectDir.toPath().resolve("out")
    checkRuntimeModuleRepository(outputDir, expected)
  }

  protected fun buildAndCheck(expected: RawDescriptorListBuilder.() -> Unit) {
    doBuild(CompileScopeTestBuilder.make().targetTypes(RuntimeModuleRepositoryTarget)).assertSuccessful()
    checkModuleRepository(expected)
  }
}