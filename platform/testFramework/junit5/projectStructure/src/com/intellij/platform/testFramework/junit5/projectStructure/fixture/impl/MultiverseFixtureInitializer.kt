// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.ProjectBuilder
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.sdkFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtureInitializer
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
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

    initializeChildren(builder, projectRootPath)

    thisLogger().info("Project structure is initialized")

    return project
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeModule(
    module: ModuleBuilderImpl,
  ) {
    val modulePath = createDirectory(module.path.resolvePath())
    val moduleFixture = projectFixture.customModuleFixture(modulePath)
    structure.addModuleFixture(module.moduleName, moduleFixture)
    val moduleInstance = moduleFixture.init()

    module.usedSdk?.let { usedSdk ->
      val sdk = structure.getSdk(usedSdk) ?: error("SDK '$usedSdk' isn't found")
      val sdkInstance = findSdk(sdk).init()
      edtWriteAction {
        val model = ModuleRootManager.getInstance(moduleInstance).modifiableModel
        model.sdk = sdkInstance
        model.commit()
      }
    }

    module.dependencies.forEach { dependency ->
      val dependencyModuleName = dependency.moduleName
      val dependencyModuleFixture = structure.findModuleFixture(dependencyModuleName) ?: error("Module '$dependencyModuleName' isn't found")
      val dependencyModuleInstance = dependencyModuleFixture.init()
      edtWriteAction {
        val model = ModuleRootManager.getInstance(moduleInstance).modifiableModel
        model.addModuleOrderEntry(dependencyModuleInstance)
        model.commit()
      }
    }

    module.contentRoots.forEach { contentRoot ->
      initializeContentRoot(contentRoot, moduleFixture)
    }

    initializeChildren(module, modulePath)

    thisLogger().info("Module '${module.moduleName}' is initialized")
  }

  // TODO allow mentioning sdk before its creation
  private fun findSdk(sdk: SdkBuilderImpl): TestFixture<Sdk> =
    sdkFixtures.getValue(sdk.name)

  private suspend fun TestFixtureInitializer.R<Project>.initializeSdk(
    sdk: SdkBuilderImpl,
  ): TestFixture<Sdk> {
    return sdkFixtures.getOrPut(sdk.name) {
      val sdkPath = createDirectory(sdk.path.resolvePath())
      initializeChildren(sdk, sdkPath)
      val sdkFixture = projectFixture.sdkFixture(sdk.name, sdk.type, sdkPath)
      sdkFixture.init()
      thisLogger().info("SDK '${sdk.name}' is initialized")
      sdkFixture
    }
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeContentRoot(
    contentRoot: ContentRootBuilderImpl,
    moduleFixture: TestFixture<Module>,
  ) {
    val contentRootPath = createDirectory(contentRoot.path.resolvePath())
    moduleFixture.customContentRootFixture(contentRootPath).init()

    contentRoot.sourceRoots.forEach { sourceRoot ->
      initializeSourceRoot(moduleFixture, contentRootPath, sourceRoot)
    }

    initializeChildren(contentRoot, contentRootPath)

    thisLogger().info("Content root '${contentRoot.path}' is initialized")
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeSourceRoot(
    moduleFixture: TestFixture<Module>,
    contentRootPath: Path,
    sourceRoot: SourceRootBuilderImpl,
  ) {
    val sourceRootPath = createDirectory(sourceRoot.path.resolvePath())
    moduleFixture.customSourceRootFixture(sourceRootPath, contentRootPath).init()

    if (!sourceRoot.isExisting) {
      initializeChildren(sourceRoot, sourceRootPath)
    }
    thisLogger().info("Source root '${sourceRoot.path}' is initialized")
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeChildren(
    container: DirectoryContainer,
    containerDirectory: Path,
  ) {
    container.sdks.forEach { nestedSdk ->
      initializeSdk(nestedSdk)
    }

    container.modules.forEach { nestedModule ->
      initializeModule(nestedModule)
    }

    container.files.forEach { file ->
      when (file) {
        is FileBuilderImplWithByteArray -> containerDirectory.writeFile(file.name, file.content)
        is FileBuilderImplWithString -> containerDirectory.writeFile(file.name, file.content)
      }

      thisLogger().info("File '${container.path}/${file.name}' is initialized")
    }

    container.directories.forEach { directory ->
      val directoryPath = createDirectory(directory.path.resolvePath())
      initializeChildren(directory, directoryPath)
      thisLogger().info("Directory '${directory.path}' is initialized")
    }
  }

  private fun String.resolvePath(): Path = projectRootPath.resolve(this).normalize().also {
    assert(it.startsWith(projectRootPath)) {
      "'$this' is outside of the project directory: this fixture does not support such paths because of the cleanup implementation"
    }
  }
}