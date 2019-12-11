// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.jps

import com.intellij.configurationStore.StoreUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.rd.attach
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.*
import com.intellij.workspace.api.ModuleDependencyItem
import com.intellij.workspace.api.ModuleEntity
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.ide.WorkspaceModelInitialTestContent
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class LegacyBridgeModuleLibraryTest {
  @Rule
  @JvmField
  var application = ApplicationRule()

  @Rule
  @JvmField
  var temporaryDirectoryRule = TemporaryDirectory()

  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  private lateinit var project: Project

  @Before
  fun prepareProject() {
    val tempDir = temporaryDirectoryRule.newPath("project").toFile()

    project = WorkspaceModelInitialTestContent.withInitialContent(TypedEntityStorageBuilder.create()) {
      ProjectManager.getInstance().createProject("testProject", File(tempDir, "testProject.ipr").path)!!
    }
    runInEdt { ProjectManagerEx.getInstanceEx().openProject(project) }

    disposableRule.disposable.attach { runInEdt { ProjectUtil.closeAndDispose(project) } }
  }

  @Test
  fun `test module library rename`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id, null) as LegacyBridgeModule
      moduleModel.commit()
      module
    }
    ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName, listOf(), emptyList())
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(moduleFile.readText().contains(antLibraryName))

    val moduleRootManager = ModuleRootManager.getInstance(module)
    moduleRootManager.modifiableModel.let { rootModel ->
      rootModel.moduleLibraryTable.getLibraryByName(antLibraryName)?.modifiableModel?.let {
        it.name = mavenLibraryName
        it.addRoot(File(project.basePath, "$mavenLibraryName.jar").path, OrderRootType.CLASSES)
        it.commit()
      }
      rootModel.commit()
    }

    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(moduleFile.readText().contains(mavenLibraryName))
    assertFalse(moduleFile.readText().contains(antLibraryName))
    assertModuleLibraryDependency(mavenLibraryName)
  }

  @Test
  fun `test module library rename and back`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id, null) as LegacyBridgeModule
      moduleModel.commit()
      module
    }
    ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName, listOf(), emptyList())
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(moduleFile.readText().contains(antLibraryName))

    val moduleRootManager = ModuleRootManager.getInstance(module)
    moduleRootManager.modifiableModel.let { rootModel ->
      rootModel.moduleLibraryTable.getLibraryByName(antLibraryName)?.modifiableModel?.let {
        it.name = mavenLibraryName
        it.commit()
      }
      rootModel.commit()
    }

    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(moduleFile.readText().contains(mavenLibraryName))
    assertFalse(moduleFile.readText().contains(antLibraryName))
    assertModuleLibraryDependency(mavenLibraryName)

    moduleRootManager.modifiableModel.let { rootModel ->
      rootModel.moduleLibraryTable.getLibraryByName(mavenLibraryName)?.modifiableModel?.let {
        it.name = antLibraryName
        it.commit()
      }
      rootModel.commit()
    }

    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(moduleFile.readText().contains(antLibraryName))
    assertFalse(moduleFile.readText().contains(mavenLibraryName))
    assertModuleLibraryDependency(antLibraryName)
  }

  @Test
  fun `test module library remove`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id, null) as LegacyBridgeModule
      moduleModel.commit()
      module
    }
    ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName,
                                                listOf(File(project.basePath, "$antLibraryName.jar").path),
                                                emptyList())
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(moduleFile.readText().contains(antLibraryName))

    val moduleRootManager = ModuleRootManager.getInstance(module)
    moduleRootManager.modifiableModel.let { rootModel ->
      rootModel.moduleLibraryTable.getLibraryByName(antLibraryName)?.let {
        rootModel.moduleLibraryTable.removeLibrary(it)
      }
      rootModel.commit()
    }

    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(!moduleFile.readText().contains(antLibraryName))
  }

  private fun assertModuleLibraryDependency(libraryName: String) {
    val moduleDependencyItem = WorkspaceModel.getInstance(project).entityStore.current
                                                                  .entities(ModuleEntity::class.java).first()
                                                                  .dependencies.last()
    assertTrue(moduleDependencyItem is ModuleDependencyItem.Exportable.LibraryDependency)
    val libraryDependency = moduleDependencyItem as ModuleDependencyItem.Exportable.LibraryDependency
    assertEquals(libraryName, libraryDependency.library.name)
  }
}