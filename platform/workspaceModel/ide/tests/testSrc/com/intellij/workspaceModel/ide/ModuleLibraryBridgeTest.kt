// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
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
  fun `test module library rename`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
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
    assertModuleLibraryDependency(moduleRootManager, mavenLibraryName)
  }

  @Test
  fun `test module library rename and back`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
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
    assertModuleLibraryDependency(moduleRootManager, mavenLibraryName)

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
    assertModuleLibraryDependency(moduleRootManager, antLibraryName)
  }

  @Ignore("Unsupported yet")
  @Test
  fun `test module library rename swapping in one transaction`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val groovyLibraryName = "groovy-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
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

    moduleRootManager.modifiableModel.let { rootModel ->
      val libraryTable = rootModel.moduleLibraryTable
      assertEquals(2, libraryTable.libraries.size)
      val antLibrary = libraryTable.libraries.find { it.name == antLibraryName }!!
      assertEquals(antLibraryName, antLibrary.name)
      assertTrue(antLibrary.getUrls(OrderRootType.CLASSES)[0].contains(mavenLibraryName))

      val mavenLibrary = libraryTable.libraries.find { it.name == mavenLibraryName }!!
      assertEquals(mavenLibraryName, mavenLibrary.name)
      assertTrue(mavenLibrary.getUrls(OrderRootType.CLASSES)[0].contains(antLibraryName))

      StoreUtil.saveDocumentsAndProjectSettings(project)
      val template = "\$MODULE_DIR\$"
      assertTrue(moduleFile.readText().replace("\r\n", "\n").contains("""<orderEntry type="module-library">
      <library name="$mavenLibraryName">
        <CLASSES>
          <root url="$template/$antLibraryName.jar" />
        </CLASSES>
        <JAVADOC />
        <SOURCES>
          <root url="$template/$antLibraryName-sources.jar" />
        </SOURCES>
      </library>
    </orderEntry>
    <orderEntry type="module-library">
      <library name="$antLibraryName">
        <CLASSES>
          <root url="$template/$mavenLibraryName.jar" />
        </CLASSES>
        <JAVADOC />
        <SOURCES>
          <root url="$template/$mavenLibraryName-sources.jar" />
        </SOURCES>
      </library>
    </orderEntry>"""))
      rootModel.commit()
    }
  }

  @Test
  fun `test module library name mangling`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val gradleLibraryName = "gradle-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
      moduleModel.commit()
      module
    }
    ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName, listOf(), emptyList())
    ModuleRootModificationUtil.addModuleLibrary(module, antLibraryName, listOf(), emptyList())
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertEquals(2, moduleFile.readText().split('\"').groupBy { it }[antLibraryName]?.size ?: 0)

    val moduleRootManager = ModuleRootManager.getInstance(module)
    moduleRootManager.modifiableModel.let { rootModel ->
      val libraries = rootModel.moduleLibraryTable.libraries
      assertEquals(2, libraries.size)
      libraries.forEach { assertEquals(antLibraryName, it.name) }

      val libraryDependencies = WorkspaceModel.getInstance(project).entityStorage.current.entities(ModuleEntity::class.java).first().dependencies.drop(1)
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
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(moduleFile.readText().contains(mavenLibraryName))
    assertTrue(moduleFile.readText().contains(gradleLibraryName))
    assertFalse(moduleFile.readText().contains(antLibraryName))

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
  fun `test module library rename dispose`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
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
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertFalse(moduleFile.readText().contains(mavenLibraryName))
    assertTrue(moduleFile.readText().contains(antLibraryName))

    assertModuleLibraryDependency(moduleRootManager, antLibraryName)
  }

  @Test
  fun `test module library remove`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
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
