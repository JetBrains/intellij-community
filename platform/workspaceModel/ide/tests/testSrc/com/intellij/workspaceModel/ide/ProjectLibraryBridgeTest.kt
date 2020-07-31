// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChanged
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProjectLibraryBridgeTest {

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
  private lateinit var events: MutableList<EntityChange<LibraryEntity>>

  @Before
  fun prepareProject() {
    project = createEmptyTestProject(temporaryDirectoryRule, disposableRule)

    events = mutableListOf()
    val messageBusConnection = project.messageBus.connect(disposableRule.disposable)
    WorkspaceModelTopics.getInstance(project).subscribeImmediately(messageBusConnection, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChanged) {
        events.addAll(event.getChanges(LibraryEntity::class.java))
      }
    })
  }

  @Test
  fun `test project library rename`() = WriteCommandAction.runWriteCommandAction(project) {
    val libraryName = "ant-lib"
    val newLibraryName = "maven-lib"
    val library = createProjectLibrary(libraryName)
    assertEquals(2, events.size)
    checkLibraryAddedEvent(events[0], libraryName)

    val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    assertNotNull(projectLibraryTable.getLibraryByName(libraryName))

    renameProjectLibrary(libraryName, newLibraryName)
    assertNull(projectLibraryTable.getLibraryByName(libraryName))
    assertNotNull(projectLibraryTable.getLibraryByName(newLibraryName))
    assertSame(library, projectLibraryTable.getLibraryByName(newLibraryName))
    assertEquals(3, events.size)
    checkLibraryReplacedEvent(events[2], libraryName, newLibraryName)

    renameProjectLibrary(newLibraryName, libraryName)
    assertNull(projectLibraryTable.getLibraryByName(newLibraryName))
    assertNotNull(projectLibraryTable.getLibraryByName(libraryName))
    assertSame(library, projectLibraryTable.getLibraryByName(libraryName))
    assertEquals(4, events.size)
    checkLibraryReplacedEvent(events[3], newLibraryName, libraryName)
  }

  @Test
  fun `test project libraries name swapping`() = WriteCommandAction.runWriteCommandAction(project) {
    val interimLibraryName = "tmp-lib"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val antLibrary = createProjectLibrary(antLibraryName)
    // Check events from listener one event for create another for add roots
    assertEquals(2, events.size)
    checkLibraryAddedEvent(events[0], antLibraryName)

    val mavenLibrary = createProjectLibrary(mavenLibraryName)
    // Check events from listener
    assertEquals(4, events.size)
    checkLibraryAddedEvent(events[2], mavenLibraryName)

    val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    assertNotNull(projectLibraryTable.getLibraryByName(antLibraryName))
    assertNotNull(projectLibraryTable.getLibraryByName(mavenLibraryName))

    // Rename library to intermediate name
    renameProjectLibrary(antLibraryName, interimLibraryName)
    assertNull(projectLibraryTable.getLibraryByName(antLibraryName))
    assertEquals(5, events.size)
    checkLibraryReplacedEvent(events[4], antLibraryName, interimLibraryName)

    // Swap name for the first library
    renameProjectLibrary(mavenLibraryName, antLibraryName)
    assertNull(projectLibraryTable.getLibraryByName(mavenLibraryName))
    assertNotNull(projectLibraryTable.getLibraryByName(antLibraryName))
    assertSame(mavenLibrary, projectLibraryTable.getLibraryByName(antLibraryName))
    assertEquals(6, events.size)
    checkLibraryReplacedEvent(events[5], mavenLibraryName, antLibraryName)

    // Swap name for the second library
    renameProjectLibrary(interimLibraryName, mavenLibraryName)
    assertNull(projectLibraryTable.getLibraryByName(interimLibraryName))
    assertNotNull(projectLibraryTable.getLibraryByName(antLibraryName))
    assertSame(antLibrary, projectLibraryTable.getLibraryByName(mavenLibraryName))
    assertEquals(7, events.size)
    checkLibraryReplacedEvent(events[6], interimLibraryName, mavenLibraryName)
  }

  @Ignore("Unsupported yet")
  @Test
  fun `test project libraries name swapping in one transaction`() = WriteCommandAction.runWriteCommandAction(project) {
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val groovyLibraryName = "groovy-lib"

    val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    projectLibraryTable.modifiableModel.let { projectLibTableModel ->
      val antLibrary = projectLibTableModel.createLibrary(antLibraryName)
      val mavenLibrary = projectLibTableModel.createLibrary(mavenLibraryName)

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

      projectLibTableModel.commit()
    }

    assertEquals(2, projectLibraryTable.libraries.size)
    val antLibrary = projectLibraryTable.libraries.find { it.name == antLibraryName }!!
    assertEquals(antLibraryName, antLibrary.name)
    assertTrue(antLibrary.getUrls(OrderRootType.CLASSES)[0].contains(mavenLibraryName))

    val mavenLibrary = projectLibraryTable.libraries.find { it.name == mavenLibraryName }!!
    assertEquals(mavenLibraryName, mavenLibrary.name)
    assertTrue(mavenLibrary.getUrls(OrderRootType.CLASSES)[0].contains(antLibraryName))

    assertEquals(2, events.size)
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(checkLibraryClassRootOnDisk(antLibraryName, "$mavenLibraryName.jar"))
    assertTrue(checkLibraryClassRootOnDisk(mavenLibraryName, "$antLibraryName.jar"))
  }

  @Test
  fun `test project library already exists exception`() = WriteCommandAction.runWriteCommandAction(project) {
    val libraryName = "ant-lib"
    val anotherLibraryName = "maven-lib"
    createProjectLibrary(libraryName)
    assertEquals(2, events.size)
    checkLibraryAddedEvent(events[0], libraryName)

    // Catch exception during library creation
    try {
      createProjectLibrary(libraryName)
    }
    catch (e: IllegalStateException) {
      assertEquals("Project library named $libraryName already exists", e.message)
    }
    // Check event was not published
    assertEquals(2, events.size)

    val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    assertEquals(1, projectLibraryTable.libraries.size)
    createProjectLibrary(anotherLibraryName)
    assertEquals(2, projectLibraryTable.libraries.size)
    assertEquals(4, events.size)
    checkLibraryAddedEvent(events[2], anotherLibraryName)

    // Catch exception during library rename
    try {
      renameProjectLibrary(anotherLibraryName, libraryName)
    }
    catch (e: IllegalStateException) {
      assertEquals("Library named $libraryName already exists", e.message)
    }
    assertEquals(2, projectLibraryTable.libraries.size)
    assertEquals(4, events.size)
  }

  @Test
  fun `test project library creation in one transaction`() = WriteCommandAction.runWriteCommandAction(project) {
    val libraryName = "ant-lib"
    val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    projectLibraryTable.modifiableModel.let {
      val library = it.createLibrary(libraryName)
      library.modifiableModel.let { mLib ->
        mLib.addRoot(File(project.basePath, "$libraryName.jar").path, OrderRootType.CLASSES)
        mLib.addRoot(File(project.basePath, "$libraryName-sources.jar").path, OrderRootType.SOURCES)
        mLib.commit()
      }
      it.commit()
    }
    assertEquals(1, events.size)
    val event = events[0]
    assertTrue(event is EntityChange.Added)
    val libraryEntity = (event as EntityChange.Added).entity
    assertEquals(libraryName, libraryEntity.name)
    assertTrue(libraryEntity.tableId is LibraryTableId.ProjectLibraryTableId)
    assertEquals(2, libraryEntity.roots.size)
  }

  @Test
  fun `test project library rename as module dependency`() = WriteCommandAction.runWriteCommandAction(project) {
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val iprFile = File(project.projectFilePath!!)
    val moduleFile = File(project.basePath, "build.iml")

    val library = createProjectLibrary(antLibraryName, withRoots = false)
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(iprFile.readText().contains(antLibraryName))

    val module = ModuleManager.getInstance(project).modifiableModel.let {
      val module = it.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
      it.commit()
      module
    }
    ModuleRootManager.getInstance(module).modifiableModel.let {
      it.addLibraryEntry(library)
      it.commit()
    }
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(moduleFile.readText().contains(antLibraryName))
    assertFalse(moduleFile.readText().contains(mavenLibraryName))

    library.modifiableModel.let {
      it.name = mavenLibraryName
      it.commit()
    }
    StoreUtil.saveDocumentsAndProjectSettings(project)
    // Check project file contains new name of lib
    assertTrue(iprFile.readText().contains(mavenLibraryName))
    assertFalse(iprFile.readText().contains(antLibraryName))
    // Check module file contains new name of lib too
    assertTrue(moduleFile.readText().contains(mavenLibraryName))
    assertFalse(moduleFile.readText().contains(antLibraryName))

    val moduleDependencyItem = WorkspaceModel.getInstance(project).entityStorage.current
                                                                  .entities(ModuleEntity::class.java).first()
                                                                  .dependencies.last()
    assertTrue(moduleDependencyItem is ModuleDependencyItem.Exportable.LibraryDependency)
    val libraryDependency = moduleDependencyItem as ModuleDependencyItem.Exportable.LibraryDependency
    assertEquals(mavenLibraryName, libraryDependency.library.name)
  }

  @Test
  fun `test project library add to module disposed`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryName = "ant-lib"

    val library = createProjectLibrary(antLibraryName, withRoots = false)
    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
      moduleModel.commit()
      module
    }

    val moduleRootManager = ModuleRootManager.getInstance(module)
    moduleRootManager.modifiableModel.let { rootModel ->
      rootModel.addLibraryEntry(library)
      rootModel.dispose()
    }
    val moduleDependencies = WorkspaceModel.getInstance(project).entityStorage.current
                                                                .entities(ModuleEntity::class.java).first()
                                                                .dependencies
    assertEquals(1, moduleDependencies.size)
    assertTrue(moduleDependencies[0] is ModuleDependencyItem.ModuleSourceDependency)
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertFalse(moduleFile.readText().contains(antLibraryName))
  }

  private fun checkLibraryAddedEvent(event: EntityChange<LibraryEntity>, libraryName: String) {
    assertTrue(event is EntityChange.Added)
    val libraryEntity = (event as EntityChange.Added).entity
    assertEquals(libraryName, libraryEntity.name)
    assertTrue(libraryEntity.tableId is LibraryTableId.ProjectLibraryTableId)
    assertEquals(0, libraryEntity.roots.size)
    checkLibraryDiskState(libraryName)
  }

  private fun checkLibraryReplacedEvent(event: EntityChange<LibraryEntity>, oldLibraryName: String, newLibraryName: String) {
    assertTrue(event is EntityChange.Replaced)
    val replaced = event as EntityChange.Replaced
    val newEntity = replaced.newEntity
    val oldEntity = replaced.oldEntity
    assertEquals(oldLibraryName, oldEntity.name)
    assertTrue(oldEntity.tableId is LibraryTableId.ProjectLibraryTableId)
    assertEquals(newLibraryName, newEntity.name)
    assertTrue(newEntity.tableId is LibraryTableId.ProjectLibraryTableId)
    assertEquals(2, newEntity.roots.size)
    checkLibraryDiskState(newLibraryName, oldLibraryName)
  }

  private fun checkLibraryDiskState(currentLibraryName: String, previousLibraryName: String = "") {
    val iprFile = File(project.projectFilePath!!)
    StoreUtil.saveDocumentsAndProjectSettings(project)
    val librariesList = JDOMUtil.load(iprFile).getChildren("component")
                                .first { it.getAttribute("name")!!.value == "libraryTable" }
                                .getChildren("library")
    assertTrue(librariesList.find { it.getAttribute("name")!!.value == currentLibraryName } != null)
    assertTrue(librariesList.find { it.getAttribute("name")!!.value == previousLibraryName } == null)
  }

  private fun checkLibraryClassRootOnDisk(libraryName: String, classFileName: String): Boolean {
    return JDOMUtil.load(File(project.projectFilePath!!)).getChildren("component")
             .first { it.getAttribute("name")!!.value == "libraryTable" }
             ?.getChildren("library")?.find { it.getAttribute("name")!!.value == libraryName }
             ?.getChild(OrderRootType.CLASSES.name())
             ?.getChild("root")
             ?.getAttribute("url")?.value?.contains(classFileName) ?: false
  }

  private fun createProjectLibrary(libraryName: String, withRoots: Boolean = true): Library {
    val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    val library = projectLibraryTable.createLibrary(libraryName)

    if (withRoots) {
      library.modifiableModel.let {
        it.addRoot(File(project.basePath, "$libraryName.jar").path, OrderRootType.CLASSES)
        it.addRoot(File(project.basePath, "$libraryName-sources.jar").path, OrderRootType.SOURCES)
        it.commit()
      }
    }
    return library
  }

  private fun renameProjectLibrary(oldLibraryName: String, newLibraryName: String) {
    val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)

    projectLibraryTable.getLibraryByName(oldLibraryName)?.modifiableModel?.let {
      it.name = newLibraryName
      it.commit()
    }
  }
}
