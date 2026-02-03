// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.property

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.java.workspace.entities.javaSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectSerializersImpl
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.io.createDirectories
import com.intellij.workspaceModel.ide.impl.jps.serialization.createProjectSerializers
import com.intellij.workspaceModel.ide.impl.jps.serialization.saveAllEntities
import com.intellij.workspaceModel.ide.toPath
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.fail


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
    val rootFolder = tempDir.resolve("testProjectX" + UUID.randomUUID().hashCode())
    val info = createProjectSerializers(rootFolder.toFile(), virtualFileManager, configurationManager)
    serializers = info.first
    configLocation = info.second
  }

  @Test
  fun createAndSave() {
    Assumptions.assumeTrue(UsefulTestCase.IS_UNDER_TEAMCITY, "Skip slow test on local run")

    PropertyChecker.customized().withIterationCount(30).withSizeHint { it % 30 }.checkScenarios {
      ImperativeCommand { env ->
        configLocation.baseDirectoryUrl.toPath().toFile().listFiles()?.forEach { it.deleteRecursively() }
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

        if (moduleEntity.isEmpty && !javaPluginPresent()) {
          if (moduleEntity.isExternal) {
            val file = prj.cache.modules.resolve("${moduleEntity.name}.xml").toFile()
            if (file.exists()) {
              fail("File should not exist ${file}. Content: ${file.readText()}")
            }
          }
          else {
            val file = prj.resolve("${moduleEntity.name}.iml").toFile()
            if (file.exists()) {
              fail("File should not exist ${file}. Content: ${file.readText()}")
            }
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

      var entitySource: EntitySource = JpsProjectFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)
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

      storage.modifyModuleEntity(moduleEntity) {
        this.contentRoots += ContentRootEntity(virtualFileManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(path.toString())), emptyList(), moduleEntity.entitySource)
      }
    }
  }
}

private fun javaPluginPresent() = PluginManagerCore.getPlugin(PluginId("com.intellij.java")) != null

internal val newEmptyWorkspace: Generator<MutableEntityStorage>
  get() = Generator.constant(MutableEntityStorage.create())
