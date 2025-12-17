// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.ProjectBuilder
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.sdkFixture
import com.intellij.testFramework.junit5.fixture.*
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

@TestOnly
internal class MultiverseFixtureInitializer(
  private val init: ProjectBuilder.() -> Unit,
) {
  private lateinit var projectFixture: TestFixture<Project>
  private lateinit var projectRootPath: Path
  private val structure = ProjectStructure()

  private val sdkFixtures = mutableMapOf<String, TestFixture<Sdk>>()

  suspend fun TestFixtureInitializer.R<Project>.initializeProjectModel(
    openProjectTask: OpenProjectTask = OpenProjectTask.build(),
    openAfterCreation: Boolean
  ): Project {
    thisLogger().info("Initializing project structure")

    val projectRootFixture = tempPathFixture()
    projectRootPath = projectRootFixture.init()

    thisLogger().info("Project root directory is created: $projectRootPath")

    projectFixture = projectFixture(pathFixture = projectRootFixture, openProjectTask = openProjectTask, openAfterCreation = openAfterCreation)
    val project = projectFixture.init()

    thisLogger().info("Project is created")

    val builder = DirectoryBuilderBase("", structure)
    builder.init()

    thisLogger().info("Project structure has been read")

    initializeChildren(builder, projectRootFixture)

    thisLogger().info("Project structure is initialized")

    return project
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeModule(
    module: ModuleBuilderImpl,
    parentFixture: TestFixture<Path>,
  ) {
    val modulePath = module.path.resolvePath()
    val modulePathFixture = dirFixture(modulePath, parentFixture)
    val moduleFixture = projectFixture.moduleFixture(modulePathFixture)
    structure.addModuleFixture(module.moduleName, moduleFixture)
    val moduleInstance = moduleFixture.init()

    module.usedSdk?.let { usedSdk ->
      val sdk = structure.getSdk(usedSdk) ?: error("SDK '$usedSdk' isn't found")
      val sdkInstance = findSdk(sdk).init()
      writeAction {
        val model = ModuleRootManager.getInstance(moduleInstance).modifiableModel
        model.sdk = sdkInstance
        model.commit()
      }
    }

    module.dependencies.forEach { dependency ->
      val dependencyModuleName = dependency.moduleName
      val dependencyModuleFixture = structure.findModuleFixture(dependencyModuleName) ?: error("Module '$dependencyModuleName' isn't found")
      val dependencyModuleInstance = dependencyModuleFixture.init()
      writeAction {
        val model = ModuleRootManager.getInstance(moduleInstance).modifiableModel
        model.addModuleOrderEntry(dependencyModuleInstance)
        model.commit()
      }
    }

    module.contentRoots.forEach { contentRoot ->
      initializeContentRoot(contentRoot, moduleFixture)
    }

    initializeChildren(module, modulePathFixture)

    thisLogger().info("Module '${module.moduleName}' is initialized")
  }

  // TODO allow mentioning sdk before its creation
  private fun findSdk(sdk: SdkBuilderImpl): TestFixture<Sdk> =
    sdkFixtures.getValue(sdk.name)

  private suspend fun TestFixtureInitializer.R<Project>.initializeSdk(
    sdk: SdkBuilderImpl,
    parentFixture: TestFixture<Path>,
  ): TestFixture<Sdk> {
    return sdkFixtures.getOrPut(sdk.name) {
      val sdkPath = sdk.path.resolvePath()
      val sdkPathFixture = dirFixture(sdkPath, parentFixture)
      initializeChildren(sdk, sdkPathFixture)
      val sdkFixture = projectFixture.sdkFixture(sdk.name, sdk.type, sdkPathFixture)
      sdkFixture.init()
      thisLogger().info("SDK '${sdk.name}' is initialized")
      sdkFixture
    }
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeContentRoot(
    contentRoot: ContentRootBuilderImpl,
    moduleFixture: TestFixture<Module>,
  ) {
    val contentRootPath = contentRoot.path.resolvePath()
    val contentRootFixture = moduleFixture.customContentRootFixture(dirFixture(contentRootPath, moduleFixture))
    contentRootFixture.init()

    contentRoot.sourceRoots.forEach { sourceRoot ->
      initializeSourceRoot(moduleFixture, contentRootFixture, sourceRoot)
    }

    initializeChildren(contentRoot, contentRootFixture)

    thisLogger().info("Content root '${contentRoot.path}' is initialized")
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeSourceRoot(
    moduleFixture: TestFixture<Module>,
    contentRootFixture: TestFixture<Path>,
    sourceRoot: SourceRootBuilderImpl,
  ) {
    val pathFixture = dirFixture(sourceRoot.path.resolvePath(), contentRootFixture)
    val sourceRootFixture = moduleFixture.customSourceRootFixture(pathFixture, contentRootFixture)
    sourceRootFixture.init()

    if (!sourceRoot.isExisting) {
      initializeChildren(sourceRoot, sourceRootFixture)
    }
    thisLogger().info("Source root '${sourceRoot.path}' is initialized")
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeChildren(
    container: DirectoryContainer,
    containerFixture: TestFixture<Path>,
  ) {
    container.sdks.forEach { nestedSdk ->
      initializeSdk(nestedSdk, containerFixture)
    }

    container.modules.forEach { nestedModule ->
      initializeModule(nestedModule, containerFixture)
    }

    container.files.forEach { file ->
      when (file) {
        is FileBuilderImplWithByteArray -> containerFixture.fileFixture(file.name, file.content).init()
        is FileBuilderImplWithString -> containerFixture.fileFixture(file.name, file.content).init()
      }

      thisLogger().info("File '${container.path}/${file.name}' is initialized")
    }

    container.directories.forEach { directory ->
      val directoryFixture = containerFixture.subDirFixture(directory.name)
      directoryFixture.init()
      initializeChildren(directory, directoryFixture)
      thisLogger().info("Directory '${directory.path}' is initialized")
    }
  }

  private fun String.resolvePath(): Path = projectRootPath.resolve(this)
}