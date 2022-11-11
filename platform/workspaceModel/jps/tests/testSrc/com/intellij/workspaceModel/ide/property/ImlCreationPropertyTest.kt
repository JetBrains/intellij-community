// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.property

import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.readText
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.jps.serialization.createProjectSerializers
import com.intellij.workspaceModel.ide.impl.jps.serialization.saveAllEntities
import com.intellij.workspaceModel.ide.impl.jps.serialization.toConfigLocation
import com.intellij.workspaceModel.ide.toPath
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.test.assertContains

class ImlCreationPropertyTest {
  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  private val temporaryDirectoryRule: TempDirectory
    get() = projectModel.baseProjectDir

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var project: Project
  private lateinit var virtualFileManager: VirtualFileUrlManager


  lateinit var rootFolder: Path

  @Before
  fun prepareProject() {
    project = projectModel.project
    virtualFileManager = VirtualFileUrlManager.getInstance(project)

    val tempDir = temporaryDirectoryRule.newDirectoryPath()
    rootFolder = tempDir.resolve("testProject")
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
      env.executeCommands(Generator.constant(CreateModule(storage)))
      val (serializers, configLocation) = createProjectSerializers(rootFolder.toFile(), virtualFileManager)
      serializers.saveAllEntities(storage, configLocation)

      storage.entities(ModuleEntity::class.java).forEach { moduleEntity ->
        val modulesXml = configLocation.baseDirectoryUrl.toPath().resolve(".idea").resolve("modules.xml").readText()
        assertContains(modulesXml, "${moduleEntity.name}.iml")
      }
    }
  }

  val ModuleEntity.isEmpty: Boolean
    get() = this.contentRoots.isEmpty() && this.javaSettings == null && this.facets.isEmpty() && this.dependencies.isEmpty()

  inner class CreateModule(private val storage: MutableEntityStorage) : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val moduleNamesGenerator = Generator.stringsOf(Generator.asciiLetters())
        .suchThat { it.isNotBlank() }
        .suchThat { ModuleId(it) !in storage }
      val moduleName = env.generateValue(moduleNamesGenerator, null)
      env.logMessage("Generate module: $moduleName")

      val configLocation = toConfigLocation(rootFolder, virtualFileManager)
      val source = JpsFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)

      storage addEntity ModuleEntity(moduleName, emptyList(), source)
    }
  }

  companion object {
    @ClassRule
    @JvmField
    val application = ApplicationRule()
  }
}

internal val newEmptyWorkspace: Generator<MutableEntityStorage>
  get() = Generator.constant(MutableEntityStorage.create())
