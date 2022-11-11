// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.property

import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.io.createDirectories
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectSerializersImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.createProjectSerializers
import com.intellij.workspaceModel.ide.impl.jps.serialization.saveAllEntities
import com.intellij.workspaceModel.ide.toPath
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class ImlCreationPropertyTest {
  @TestDisposable
  lateinit var disposableRule: Disposable

  @TempDir
  lateinit var tempDir: Path

  private lateinit var virtualFileManager: VirtualFileUrlManager


  lateinit var serializers: JpsProjectSerializersImpl
  lateinit var configLocation: JpsProjectConfigLocation

  @BeforeEach
  fun prepareProject() {
    virtualFileManager = VirtualFileUrlManagerImpl()

    val rootFolder = tempDir.resolve("testProject")
    val info = createProjectSerializers(rootFolder.toFile(), virtualFileManager)
    serializers = info.first
    configLocation = info.second
  }

  @Test
  fun createAndSave() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        val workspace = env.generateValue(newEmptyWorkspace, "Generate empty workspace")
        env.executeCommands(Generator.constant(CreateAndSave(workspace)))
      }
    }
  }

  inner class CreateAndSave(private val storage: MutableEntityStorage) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      env.executeCommands(Generator.sampledFrom(
        CreateModule(storage),
        CreateContentRoot(storage),
      ))
      serializers.saveAllEntities(storage, configLocation)

      storage.entities(ModuleEntity::class.java).forEach { moduleEntity ->
        val modulesXml = configLocation.baseDirectoryUrl.toPath().resolve(".idea").resolve("modules.xml").readText()
        assertContains(modulesXml, "${moduleEntity.name}.iml")

        if (moduleEntity.isEmpty) {
          assertFalse(configLocation.baseDirectoryUrl.toPath().resolve("${moduleEntity.name}.iml").exists())
        }
        else {
          assertTrue(configLocation.baseDirectoryUrl.toPath().resolve("${moduleEntity.name}.iml").exists())
        }
      }

      storage.entities(ContentRootEntity::class.java).forEach { contentRootEntity ->
        val moduleText = configLocation.baseDirectoryUrl.toPath().resolve("${contentRootEntity.module.name}.iml").readText()
        assertContains(moduleText, contentRootEntity.url.fileName)
      }
    }
  }

  val ModuleEntity.isEmpty: Boolean
    get() = this.contentRoots.isEmpty() && this.javaSettings == null && this.facets.isEmpty() && this.dependencies.isEmpty()

  inner class CreateModule(private val storage: MutableEntityStorage) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val moduleNamesGenerator = Generator.stringsOf(Generator.asciiLetters())
        .suchThat { it.isNotBlank() }
        .suchThat { it.lowercase() !in storage.entities(ModuleEntity::class.java).map { it.name.lowercase() } }
      val moduleName = env.generateValue(moduleNamesGenerator, null)
      env.logMessage("Generate module: $moduleName")

      val source = JpsFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)
      storage addEntity ModuleEntity(moduleName, emptyList(), source)
    }
  }

  inner class CreateContentRoot(private val storage: MutableEntityStorage) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val modules = storage.entities(ModuleEntity::class.java).toList()
      if (modules.isEmpty()) return
      val moduleEntity = env.generateValue(Generator.sampledFrom(modules), null)

      val contentRootPathGenerator = Generator.stringsOf(Generator.asciiLetters())
        .suchThat { it.isNotBlank() }
        .suchThat { moduleEntity.contentRoots.map { it.url.url }.none { url -> it in url } }
      val contentRootPath = env.generateValue(contentRootPathGenerator, null)

      val path = configLocation.baseDirectoryUrl.toPath().resolve(contentRootPath).createDirectories()

      storage addEntity ContentRootEntity(virtualFileManager.fromPath(path.toString()), emptyList(), moduleEntity.entitySource) {
        this.module = moduleEntity
      }
    }
  }
}

internal val newEmptyWorkspace: Generator<MutableEntityStorage>
  get() = Generator.constant(MutableEntityStorage.create())
