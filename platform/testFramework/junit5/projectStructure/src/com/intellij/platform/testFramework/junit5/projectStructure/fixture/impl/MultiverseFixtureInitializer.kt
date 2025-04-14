// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.ProjectBuilder
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtureInitializer
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.io.path.Path

@TestOnly
internal class MultiverseFixtureInitializer(
  private val init: ProjectBuilder.() -> Unit,
) {
  private lateinit var projectFixture: TestFixture<Project>
  private lateinit var projectRootPath: Path
  private val structure = ProjectStructure()

  private val sdkFixtures = mutableMapOf<String, TestFixture<Sdk>>()

  suspend fun TestFixtureInitializer.R<Project>.initializeProjectModel(): Project {
    projectFixture = projectFixture()
    val project = projectFixture.init()

    projectRootPath = project.basePath?.let { Path(it) } ?: error("Project base path is not available")

    val projectRootAsFixture = dirFixture(projectRootPath)
    projectRootAsFixture.init()

    val builder = DirectoryBuilderBase("", structure)
    builder.init()

    initializeChildren(builder, projectRootAsFixture)

    return project
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeModule(
    module: ModuleBuilderImpl
  ) {
    val modulePath = module.path.resolvePath()
    val modulePathFixture = dirFixture(modulePath)
    val moduleFixture = projectFixture.moduleFixture(modulePathFixture)
    val moduleInstance = moduleFixture.init()

    module.usedSdk?.let { usedSdk ->
      val sdk = structure.getSdk(usedSdk) ?: error("SDK '$usedSdk' isn't found")
      val sdkInstance = initializeSdk(sdk).init()
      writeAction {
        val model = ModuleRootManager.getInstance(moduleInstance).modifiableModel
        model.sdk = sdkInstance
        model.commit()
      }
    }

    module.contentRoots.forEach { contentRoot ->
      initializeContentRoot(contentRoot, moduleFixture)
    }

    initializeChildren(module, modulePathFixture)
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeSdk(
    sdk: SdkBuilderImpl
  ): TestFixture<Sdk> {
    return sdkFixtures.getOrPut(sdk.name) {
      val sdkPath = sdk.path.resolvePath()
      val sdkPathFixture = dirFixture(sdkPath)
      initializeChildren(sdk, sdkPathFixture)
      val sdkFixture = projectFixture.sdkFixture(sdk.name, sdk.type, sdkPathFixture)
      sdkFixture.init()
      sdkFixture
    }
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeContentRoot(
    contentRoot: ContentRootBuilderImpl,
    moduleFixture: TestFixture<Module>,
  ) {
    val contentRootPath = contentRoot.path.resolvePath()
    val contentRootFixture = moduleFixture.customContentRootFixture(dirFixture(contentRootPath))
    contentRootFixture.init()

    contentRoot.sourceRoots.forEach { sourceRoot ->
      initializeSourceRoot(moduleFixture, contentRootFixture, sourceRoot)
    }

    initializeChildren(contentRoot, contentRootFixture)
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeSourceRoot(
    moduleFixture: TestFixture<Module>,
    contentRootFixture: TestFixture<Path>,
    sourceRoot: SourceRootBuilderImpl,
  ) {
    val pathFixture = dirFixture(sourceRoot.path.resolvePath())
    val sourceRootFixture = moduleFixture.customSourceRootFixture(pathFixture, contentRootFixture)
    sourceRootFixture.init()

    if (!sourceRoot.isExisting) {
      initializeChildren(sourceRoot, sourceRootFixture)
    }
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeChildren(
    container: DirectoryContainer,
    containerFixture: TestFixture<Path>,
  ) {
    container.modules.forEach { nestedModule ->
      initializeModule(nestedModule)
    }

    container.files.forEach { file ->
      when (file) {
        is FileBuilderImplWithByteArray -> containerFixture.fileFixture(file.name, file.content).init()
        is FileBuilderImplWithString -> containerFixture.fileFixture(file.name, file.content).init()
      }
    }

    container.directories.forEach { directory ->
      val directoryFixture = containerFixture.subDirFixture(directory.name)
      directoryFixture.init()
      initializeChildren(directory, directoryFixture)
    }

    container.sdks.forEach { nestedSdk ->
      initializeSdk(nestedSdk)
    }
  }

  private fun String.resolvePath(): Path = projectRootPath.resolve(this)
}