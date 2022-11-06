// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

class ModuleLibraryBridgeTest {
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
    project = createEmptyTestProject(temporaryDirectoryRule, disposableRule)
  }

  @Test
  fun `test module library rename`() {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")

    val module = WriteCommandAction.writeCommandAction(project).compute<ModuleBridge, RuntimeException> {
      val module = ModuleManager.getInstance(project).getModifiableModel().let { moduleModel ->
        val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
        moduleModel.commit()
        module
      }
      ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName, listOf(), emptyList())
      module
    }

    val moduleRootManager = WriteCommandAction.writeCommandAction(project).compute<ModuleRootManager, RuntimeException> {
      val moduleRootManager = ModuleRootManager.getInstance(module)
      moduleRootManager.modifiableModel.let { rootModel ->
        rootModel.moduleLibraryTable.getLibraryByName(antLibraryName)?.modifiableModel?.let {
          it.name = mavenLibraryName
          it.addRoot(File(project.basePath, "$mavenLibraryName.jar").path, OrderRootType.CLASSES)
          it.commit()
        }
        rootModel.commit()
      }
      moduleRootManager
    }

    assertModuleLibraryDependency(moduleRootManager, mavenLibraryName)
  }

  @Test
  fun `test module library rename and back`() {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")

    val module = WriteCommandAction.writeCommandAction(project).compute<ModuleBridge, RuntimeException> {
      val module = ModuleManager.getInstance(project).getModifiableModel().let { moduleModel ->
        val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
        moduleModel.commit()
        module
      }
      ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName, listOf(), emptyList())
      module
    }
    
    val moduleRootManager = WriteCommandAction.writeCommandAction(project).compute<ModuleRootManager, RuntimeException> {
      val moduleRootManager = ModuleRootManager.getInstance(module)
      moduleRootManager.modifiableModel.let { rootModel ->
        rootModel.moduleLibraryTable.getLibraryByName(antLibraryName)?.modifiableModel?.let {
          it.name = mavenLibraryName
          it.commit()
        }
        rootModel.commit()
      }
      moduleRootManager
    }
    
    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      assertModuleLibraryDependency(moduleRootManager, mavenLibraryName)

      moduleRootManager.modifiableModel.let { rootModel ->
        rootModel.moduleLibraryTable.getLibraryByName(mavenLibraryName)?.modifiableModel?.let {
          it.name = antLibraryName
          it.commit()
        }
        rootModel.commit()
      }
    }

    assertModuleLibraryDependency(moduleRootManager, antLibraryName)
  }

  @Ignore("Unsupported yet")
  @Test
  fun `test module library rename swapping in one transaction`() {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val groovyLibraryName = "groovy-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")

    val rootModel = WriteCommandAction.writeCommandAction(project).compute<ModifiableRootModel, RuntimeException> {
      val module = ModuleManager.getInstance(project).getModifiableModel().let { moduleModel ->
        val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
        moduleModel.commit()
        module
      }

      val moduleRootManager = ModuleRootManager.getInstance(module)
      moduleRootManager.modifiableModel.let { rootModel ->
        val modifiableModel = rootModel.moduleLibraryTable.modifiableModel
        val antLibrary = modifiableModel.createLibrary(antLibraryName)
        val mavenLibrary = modifiableModel.createLibrary(mavenLibraryName)

        var antLibraryModel = antLibrary.modifiableModel
        antLibraryModel.addRoot(File(project.basePath, "$antLibraryName.jar").path, OrderRootType.CLASSES)
        antLibraryModel.addRoot(File(project.basePath, "$antLibraryName-sources.jar").path, OrderRootType.SOURCES)
        antLibraryModel.name = groovyLibraryName
        antLibraryModel.commit()

        val mavenLibraryModel = mavenLibrary.modifiableModel
        mavenLibraryModel.addRoot(File(project.basePath, "$mavenLibraryName.jar").path, OrderRootType.CLASSES)
        mavenLibraryModel.addRoot(File(project.basePath, "$mavenLibraryName-sources.jar").path, OrderRootType.SOURCES)
        mavenLibraryModel.name = antLibraryName
        mavenLibraryModel.commit()

        antLibraryModel = antLibrary.modifiableModel
        antLibraryModel.name = mavenLibraryName
        antLibraryModel.commit()

        modifiableModel.commit()
        rootModel.commit()
      }

      val rootModel = moduleRootManager.modifiableModel
      val libraryTable = rootModel.moduleLibraryTable
      assertEquals(2, libraryTable.libraries.size)
      val antLibrary = libraryTable.libraries.find { it.name == antLibraryName }!!
      assertEquals(antLibraryName, antLibrary.name)
      assertTrue(antLibrary.getUrls(OrderRootType.CLASSES)[0].contains(mavenLibraryName))

      val mavenLibrary = libraryTable.libraries.find { it.name == mavenLibraryName }!!
      assertEquals(mavenLibraryName, mavenLibrary.name)
      assertTrue(mavenLibrary.getUrls(OrderRootType.CLASSES)[0].contains(antLibraryName))
      rootModel
    }

    runBlocking {
      withContext(Dispatchers.EDT) {
        ApplicationManager.getApplication().runWriteAction {
          rootModel.commit()
        }
      }
    }
  }

  @Test
  fun `test module library name mangling`() {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val gradleLibraryName = "gradle-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")

    val module = WriteCommandAction.writeCommandAction(project).compute<ModuleBridge, RuntimeException> {
      val module = ModuleManager.getInstance(project).getModifiableModel().let { moduleModel ->
        val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
        moduleModel.commit()
        module
      }
      ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName, listOf(), emptyList())
      ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName, listOf(), emptyList())
      module
    }
    
    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      val moduleRootManager = ModuleRootManager.getInstance(module)
      moduleRootManager.modifiableModel.let { rootModel ->
        val libraries = rootModel.moduleLibraryTable.libraries
        assertEquals(2, libraries.size)
        libraries.forEach { assertEquals(antLibraryName, it.name) }

        val libraryDependencies = WorkspaceModel.getInstance(project).entityStorage.current.entities(
          ModuleEntity::class.java).first().dependencies.drop(1)
        assertEquals(2, libraryDependencies.size)
        val libraryDepOne = libraryDependencies[0] as ModuleDependencyItem.Exportable.LibraryDependency
        val libraryDepTwo = libraryDependencies[1] as ModuleDependencyItem.Exportable.LibraryDependency
        // Check name mangling for libraries with the same name
        assertTrue(libraryDepOne.library.name != libraryDepTwo.library.name)

        // Rename both libraries
        rootModel.moduleLibraryTable.getLibraryByName(antLibraryName)?.modifiableModel?.let {
          it.name = mavenLibraryName
          it.addRoot(File(project.basePath, "$mavenLibraryName.jar").path, OrderRootType.CLASSES)
          it.commit()
        }
        rootModel.moduleLibraryTable.getLibraryByName(antLibraryName)?.modifiableModel?.let {
          it.name = gradleLibraryName
          it.addRoot(File(project.basePath, "$gradleLibraryName.jar").path, OrderRootType.CLASSES)
          it.commit()
        }
        rootModel.commit()
      }
    }
    
    val libraryDependencies = WorkspaceModel.getInstance(project).entityStorage.current
      .entities(ModuleEntity::class.java).first()
      .dependencies.drop(1)
    assertEquals(2, libraryDependencies.size)
    val libraryDepOne = libraryDependencies[0] as ModuleDependencyItem.Exportable.LibraryDependency
    val libraryDepTwo = libraryDependencies[1] as ModuleDependencyItem.Exportable.LibraryDependency
    // Check no name mangling for libraries
    assertTrue(listOf(libraryDepOne.library.name, libraryDepTwo.library.name).containsAll(listOf(mavenLibraryName, gradleLibraryName)))
  }

  @Test
  fun `test module library rename dispose`() {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val moduleFile = File(project.basePath, "$moduleName.iml")

    val moduleRootManager = WriteCommandAction.writeCommandAction(project).compute<ModuleRootManager, RuntimeException> {
      val module = ModuleManager.getInstance(project).getModifiableModel().let { moduleModel ->
        val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
        moduleModel.commit()
        module
      }
      ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName, listOf(), emptyList())

      val moduleRootManager = ModuleRootManager.getInstance(module)
      assertModuleLibraryDependency(moduleRootManager, antLibraryName)
      moduleRootManager.modifiableModel.let { rootModel ->
        rootModel.moduleLibraryTable.getLibraryByName(antLibraryName)?.modifiableModel?.let {
          it.name = mavenLibraryName
          it.addRoot(File(project.basePath, "$mavenLibraryName.jar").path, OrderRootType.CLASSES)
          Disposer.dispose(it)
        }
        rootModel.dispose()
      }
      moduleRootManager
    }

    assertModuleLibraryDependency(moduleRootManager, antLibraryName)
  }

  @Test
  fun `test module library instance after module root commit`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val gradleLibraryName = "gradle-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).getModifiableModel().let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
      moduleModel.commit()
      module
    }
    ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName, listOf(), emptyList())
    ModuleRootModificationUtil.addModuleLibrary(module, mavenLibraryName, listOf(File(project.basePath, "$mavenLibraryName.jar").path),
                                                emptyList())
    ModuleRootModificationUtil.addModuleLibrary(module, gradleLibraryName, listOf(), emptyList())
    var moduleLibraryTable = ModuleRootComponentBridge.getInstance(module).getModuleLibraryTable()
    val antLibraryBridgeOne = moduleLibraryTable.getLibraryByName(antLibraryName)
    val mavenLibraryBridgeOne = moduleLibraryTable.getLibraryByName(mavenLibraryName)
    val gradleLibraryBridgeOne = moduleLibraryTable.getLibraryByName(gradleLibraryName)

    val moduleRootManager = ModuleRootManager.getInstance(module)
    moduleRootManager.modifiableModel.let { rootModel ->
      rootModel.moduleLibraryTable.getLibraryByName(mavenLibraryName)?.modifiableModel?.let {
        it.removeRoot(File(project.basePath, "$mavenLibraryName.jar").path, OrderRootType.CLASSES)
        it.commit()
      }
      rootModel.moduleLibraryTable.getLibraryByName(gradleLibraryName)?.modifiableModel?.let {
        it.name = "New Name"
        it.commit()
      }
      rootModel.setInvalidSdk("Test", "")
      rootModel.commit()
    }

    moduleLibraryTable = ModuleRootComponentBridge.getInstance(module).getModuleLibraryTable()
    val antLibraryBridgeTwo = moduleLibraryTable.getLibraryByName(antLibraryName)
    val mavenLibraryBridgeTwo = moduleLibraryTable.getLibraryByName(mavenLibraryName)
    val gradleLibraryBridgeTwo = moduleLibraryTable.getLibraryByName("New Name")

    assertTrue(antLibraryBridgeOne === antLibraryBridgeTwo)
    assertTrue(mavenLibraryBridgeOne !== mavenLibraryBridgeTwo)
    assertTrue(gradleLibraryBridgeOne !== gradleLibraryBridgeTwo)
  }

  @Test
  fun `check module library dispose`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val gradleLibraryName = "gradle-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).getModifiableModel().let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
      moduleModel.commit()
      module
    }
    ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName, listOf(), emptyList())
    ModuleRootModificationUtil.addModuleLibrary(module, mavenLibraryName, listOf(), emptyList())
    ModuleRootModificationUtil.addModuleLibrary(module, gradleLibraryName, listOf(), emptyList())
    var moduleLibraryTable = ModuleRootComponentBridge.getInstance(module).getModuleLibraryTable()
    val antLibraryBridgeOrigin = moduleLibraryTable.getLibraryByName(antLibraryName)
    val mavenLibraryBridgeOrigin = moduleLibraryTable.getLibraryByName(mavenLibraryName)
    val gradleLibraryBridgeOrigin = moduleLibraryTable.getLibraryByName(gradleLibraryName)

    val rootModel = ModuleRootManager.getInstance(module).modifiableModel
    val antLibraryBridgeCopy = rootModel.moduleLibraryTable.getLibraryByName(antLibraryName)
    rootModel.moduleLibraryTable.removeLibrary(antLibraryBridgeCopy!!)

    val mavenLibraryBridgeCopy = rootModel.moduleLibraryTable.getLibraryByName(mavenLibraryName)

    val gradleLibraryBridgeCopy = rootModel.moduleLibraryTable.getLibraryByName(gradleLibraryName)
    rootModel.moduleLibraryTable.getLibraryByName(gradleLibraryName)?.modifiableModel?.let {
      it.name = "New Name"
      it.commit()
    }
    rootModel.commit()

    moduleLibraryTable = ModuleRootComponentBridge.getInstance(module).getModuleLibraryTable()

    // Check both instances of removed library disposed
    assertNull(moduleLibraryTable.getLibraryByName(antLibraryName))
    assertTrue(antLibraryBridgeOrigin !== antLibraryBridgeCopy)
    assertTrue((antLibraryBridgeOrigin as LibraryBridge).isDisposed)
    assertTrue((antLibraryBridgeCopy as LibraryBridge).isDisposed)

    // Check copy instance is disposed for not modified library
    val mavenLibraryBridgeTwo = moduleLibraryTable.getLibraryByName(mavenLibraryName)
    assertTrue(mavenLibraryBridgeTwo === mavenLibraryBridgeOrigin)
    assertTrue((mavenLibraryBridgeCopy as LibraryBridge).isDisposed)
    assertFalse((mavenLibraryBridgeOrigin as LibraryBridge).isDisposed)

    // Check origin instance is disposed for modified library
    val gradleLibraryBridgeTwo = moduleLibraryTable.getLibraryByName("New Name")
    assertTrue(gradleLibraryBridgeTwo === gradleLibraryBridgeCopy)
    assertTrue((gradleLibraryBridgeOrigin as LibraryBridge).isDisposed)
    assertFalse((gradleLibraryBridgeCopy as LibraryBridge).isDisposed)
  }

  @Test
  fun `test module library remove`() {
    val moduleName = "build"
    val antLibraryName = "ant-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")

    val module = WriteCommandAction.writeCommandAction(project).compute<ModuleBridge, RuntimeException> {
      val module = ModuleManager.getInstance(project).getModifiableModel().let { moduleModel ->
        val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
        moduleModel.commit()
        module
      }
      ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName,
                                                  listOf(File(project.basePath, "$antLibraryName.jar").path),
                                                  emptyList())
      module
    }
    
    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      val moduleRootManager = ModuleRootManager.getInstance(module)
      moduleRootManager.modifiableModel.let { rootModel ->
        rootModel.moduleLibraryTable.getLibraryByName(antLibraryName)?.let {
          rootModel.moduleLibraryTable.removeLibrary(it)
        }
        rootModel.commit()
      }
    }
  }

  @Test
  fun `test module dispose`() {
    val moduleName = "build"
    val antLibraryName = "ant-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")

    val module = WriteCommandAction.writeCommandAction(project).compute<ModuleBridge, RuntimeException> {
      val module = ModuleManager.getInstance(project).getModifiableModel().let { moduleModel ->
        val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
        moduleModel.commit()
        module
      }
      ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName,
                                                  listOf(File(project.basePath, "$antLibraryName.jar").path),
                                                  emptyList())
      module
    }

    runBlocking {
      withContext(Dispatchers.EDT) {
        ModuleManager.getInstance(project).disposeModule(module)
      }
    }

    ModuleManager.getInstance(project).modules.forEach { existingModule ->
      ModuleRootManager.getInstance(existingModule).orderEntries.filterIsInstance<LibraryOrSdkOrderEntry>().forEach { orderEntry ->
        orderEntry.getRootFiles(OrderRootType.SOURCES)
      }
    }
  }

  private fun assertModuleLibraryDependency(moduleRootManager: ModuleRootManager, libraryName: String) {
    moduleRootManager.modifiableModel.let { rootModel ->
      val libraries = rootModel.moduleLibraryTable.libraries
      assertEquals(1, libraries.size)
      libraries.forEach { assertEquals(libraryName, it.name) }
      rootModel.dispose()
    }
    val moduleDependencyItem = WorkspaceModel.getInstance(project).entityStorage.current
      .entities(ModuleEntity::class.java).first()
      .dependencies.last()
    assertTrue(moduleDependencyItem is ModuleDependencyItem.Exportable.LibraryDependency)
    val libraryDependency = moduleDependencyItem as ModuleDependencyItem.Exportable.LibraryDependency
    assertEquals(libraryName, libraryDependency.library.name)
  }
}
