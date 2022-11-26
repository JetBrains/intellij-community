// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.property

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.io.createDirectories
import com.intellij.util.io.readText
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectSerializersImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.createProjectSerializers
import com.intellij.workspaceModel.ide.impl.jps.serialization.saveAllEntities
import com.intellij.workspaceModel.ide.toPath
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains


// TODO: Cover case in com.intellij.workspaceModel.ide.impl.jps.serialization.JpsSplitModuleAndContentRoot.load module without java custom settings
@TestApplication
class ImlCreationPropertyTest {
  @TestDisposable
  lateinit var disposableRule: Disposable

  @TempDir
  lateinit var tempDir: Path

  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private lateinit var virtualFileManager: VirtualFileUrlManager

  lateinit var serializers: JpsProjectSerializersImpl
  lateinit var configLocation: JpsProjectConfigLocation

  @BeforeEach
  fun prepareProject() {
    virtualFileManager = VirtualFileUrlManagerImpl()

    val configurationManager = ExternalStorageConfigurationManager.getInstance(projectModel.project)
    configurationManager.isEnabled = true
    val rootFolder = tempDir.resolve("testProject")
    val info = createProjectSerializers(rootFolder.toFile(), virtualFileManager, configurationManager)
    serializers = info.first
    configLocation = info.second
  }

  @Test
  fun createAndSave() {
    PropertyChecker.checkScenarios {
      ImperativeCommand { env ->
        tempDir.toFile().listFiles().forEach { it.deleteRecursively() }
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
        val modulesXml = if (moduleEntity.isExternal) prj.cache.project.modulesXml.readText() else prj.idea.modulesXml.readText()
        assertContains(modulesXml, "${moduleEntity.name}.iml", ignoreCase = true,
                       "Link to module.iml is not found in modules.xml. ${moduleEntity.name}")

        if (moduleEntity.isEmpty) {
          if (moduleEntity.isExternal) {
            UsefulTestCase.assertDoesntExist(prj.cache.modules.resolve("${moduleEntity.name}.xml").toFile())
          }
          else {
            UsefulTestCase.assertDoesntExist(prj.resolve("${moduleEntity.name}.iml").toFile())
          }
        }
        else {
          if (moduleEntity.isExternal) {
            UsefulTestCase.assertExists(prj.cache.modules.resolve("${moduleEntity.name}.xml").toFile())
          }
          else {
            UsefulTestCase.assertExists(prj.resolve("${moduleEntity.name}.iml").toFile())
          }
        }
      }

      storage.entities(ContentRootEntity::class.java).forEach { contentRootEntity ->
        assertContains(contentRootEntity.imlText, contentRootEntity.url.fileName)
      }
    }
  }

  val ModuleEntity.isEmpty: Boolean
    get() = this.contentRoots.isEmpty() && this.javaSettings == null && this.facets.isEmpty() && this.dependencies.isEmpty()

  val WorkspaceEntity.imlText: String
    get() {
      val module = when (this) {
        is ModuleEntity -> this
        is ContentRootEntity -> this.module
        else -> error("Unsupported yet")
      }
      return if (this.isExternal) prj.cache.modules.resolve("${module.name}.xml").readText()
      else prj.resolve("${module.name}.iml").readText()
    }

  val WorkspaceEntity.isExternal: Boolean
    get() = this.entitySource is JpsImportedEntitySource

  val prj: Path
    get() = configLocation.baseDirectoryUrl.toPath()

  val Path.cache: Path
    get() = this.resolve("cache")

  val Path.project: Path
    get() = this.resolve("project")

  val Path.modulesXml: Path
    get() = this.resolve("modules.xml")
  val Path.modules: Path
    get() = this.resolve("modules")
  val Path.idea: Path
    get() = this.resolve(".idea")

  fun EntitySource.external(): JpsImportedEntitySource {
    return JpsImportedEntitySource(this as JpsFileEntitySource, "GRADLE", true)
  }

  inner class CreateModule(private val storage: MutableEntityStorage) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val moduleNamesGenerator = Generator.stringsOf(Generator.asciiLetters())
        .suchThat { it.isNotBlank() }
        .suchThat { it.lowercase() !in storage.entities(ModuleEntity::class.java).map { it.name.lowercase() } }
      val moduleName = env.generateValue(moduleNamesGenerator, null)
      env.logMessage("Generate module: $moduleName")

      var entitySource: EntitySource = JpsFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)
      if (env.generateValue(Generator.booleans(), null)) {
        entitySource = entitySource.external()
      }
      storage addEntity ModuleEntity(moduleName, emptyList(), entitySource)
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
