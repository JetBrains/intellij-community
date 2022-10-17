// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.junit.*
import org.junit.Assert.*

class ProjectLibraryBridgeTest {
  companion object {
    @ClassRule
    @JvmField
    var application = ApplicationRule()
  }

  @Rule
  @JvmField
  var projectModel = ProjectModelRule()

  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  private lateinit var project: Project
  private lateinit var events: MutableList<EntityChange<LibraryEntity>>
  private lateinit var excludedUrlEvents: MutableList<EntityChange<ExcludeUrlEntity>>

  @Before
  fun prepareProject() {
    project = projectModel.project

    events = mutableListOf()
    excludedUrlEvents = mutableListOf()
    project.messageBus.connect(disposableRule.disposable).subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        events.addAll(event.getChanges(LibraryEntity::class.java))
        excludedUrlEvents.addAll(event.getChanges(ExcludeUrlEntity::class.java))
      }
    })
  }

  @Test
  fun `test project library rename`() {
    val libraryName = "ant-lib"
    val newLibraryName = "maven-lib"

    val library = createProjectLibrary(libraryName)
    assertEquals(2, events.size)
    checkLibraryAddedEvent(events[0], libraryName)
    projectModel.renameLibrary(library, newLibraryName)
    assertEquals(events.toString(), 3, events.size)
    checkLibraryReplacedEvent(events[2], libraryName, newLibraryName)

    projectModel.renameLibrary(library, libraryName)
    assertEquals(events.toString(), 4, events.size)
    checkLibraryReplacedEvent(events[3], newLibraryName, libraryName)
  }

  @Test
  fun `test project libraries name swapping`() {
    val interimLibraryName = "tmp-lib"
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val antLibrary = createProjectLibrary(antLibraryName)
    // Check events from listener one event for create another for add roots
    assertEquals(2, events.size)
    checkLibraryAddedEvent(events[0], antLibraryName)
    val mavenLibrary = createProjectLibrary(mavenLibraryName)
    assertEquals(4, events.size)
    checkLibraryAddedEvent(events[2], mavenLibraryName)

    projectModel.renameLibrary(antLibrary, interimLibraryName)
    assertEquals(5, events.size)

    checkLibraryReplacedEvent(events[4], antLibraryName, interimLibraryName)

    projectModel.renameLibrary(mavenLibrary, antLibraryName)
    assertEquals(6, events.size)
    checkLibraryReplacedEvent(events[5], mavenLibraryName, antLibraryName)

    projectModel.renameLibrary(antLibrary, mavenLibraryName)
    assertEquals(7, events.size)
    checkLibraryReplacedEvent(events[6], interimLibraryName, mavenLibraryName)
  }

  @Ignore("Unsupported yet")
  @Test
  fun `test project libraries name swapping in one transaction`() {
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"
    val groovyLibraryName = "groovy-lib"

    runWriteActionAndWait {
      val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
      projectLibraryTable.modifiableModel.let { projectLibTableModel ->
        val antLibrary = projectLibTableModel.createLibrary(antLibraryName)
        val mavenLibrary = projectLibTableModel.createLibrary(mavenLibraryName)

        var antLibraryModel = antLibrary.modifiableModel
        antLibraryModel.addRoot(projectModel.baseProjectDir.newVirtualFile("$antLibraryName.jar"), OrderRootType.CLASSES)
        antLibraryModel.addRoot(projectModel.baseProjectDir.newVirtualFile("$antLibraryName-sources.jar"), OrderRootType.SOURCES)
        antLibraryModel.name = groovyLibraryName
        antLibraryModel.commit()

        val mavenLibraryModel = mavenLibrary.modifiableModel
        mavenLibraryModel.addRoot(projectModel.baseProjectDir.newVirtualFile("$mavenLibraryName.jar"), OrderRootType.CLASSES)
        mavenLibraryModel.addRoot(projectModel.baseProjectDir.newVirtualFile("$mavenLibraryName-sources.jar"), OrderRootType.SOURCES)
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
    }
  }

  @Test
  fun `test project library already exists exception`() {
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
    val anotherLibrary = createProjectLibrary(anotherLibraryName)
    assertEquals(2, projectLibraryTable.libraries.size)
    assertEquals(4, events.size)
    checkLibraryAddedEvent(events[2], anotherLibraryName)

    runWriteActionAndWait {
      try {
        projectModel.renameLibrary(anotherLibrary, libraryName)
      }
      catch (e: IllegalStateException) {
        assertEquals("Library named $libraryName already exists", e.message)
      }
    }

    assertEquals(2, projectLibraryTable.libraries.size)
    assertEquals(4, events.size)
  }

  @Test
  fun `test project library creation in one transaction`() {
    val libraryName = "ant-lib"
    projectModel.addProjectLevelLibrary(libraryName) {
      it.addRoot(projectModel.baseProjectDir.newVirtualFile("$libraryName.jar"), OrderRootType.CLASSES)
      it.addRoot(projectModel.baseProjectDir.newVirtualFile("$libraryName-sources.jar"), OrderRootType.SOURCES)
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
  fun `test project library instance after rollback`() {
    val libraryName = "ant-lib"
    val anotherLibraryName = "maven-lib"

    val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    val libraryBridgeOne = projectModel.addProjectLevelLibrary(libraryName) {
      it.addRoot(projectModel.baseProjectDir.newVirtualFile("$libraryName.jar"), OrderRootType.CLASSES)
      it.addRoot(projectModel.baseProjectDir.newVirtualFile("$libraryName-sources.jar"), OrderRootType.SOURCES)
    }

    val libraryBridgeTwo = projectLibraryTable.getLibraryByName(libraryName)
    assertTrue(libraryBridgeOne === libraryBridgeTwo)

    var libraryBridgeTree: Library?
    projectLibraryTable.modifiableModel.let {
      libraryBridgeTree = it.libraries[0]
      assertTrue(libraryBridgeTwo === libraryBridgeTree)

      libraryBridgeTree!!.modifiableModel.let { mLib->
        mLib.name = anotherLibraryName
        mLib
      }
    }
    val libraryBridgeFour = projectLibraryTable.getLibraryByName(libraryName)
    assertTrue(libraryBridgeTree === libraryBridgeFour)
    assertEquals(libraryName, libraryBridgeFour!!.name)
  }

  @Test
  fun `test project library rename as module dependency`() {
    val antLibraryName = "ant-lib"
    val mavenLibraryName = "maven-lib"

    val library = createProjectLibrary(antLibraryName, withRoots = false)

    val module = projectModel.createModule()
    ModuleRootModificationUtil.addDependency(module, library)
    projectModel.renameLibrary(library, mavenLibraryName)

    val moduleDependencyItem = WorkspaceModel.getInstance(project).entityStorage.current
      .entities(ModuleEntity::class.java).first()
      .dependencies.last()
    assertTrue(moduleDependencyItem is ModuleDependencyItem.Exportable.LibraryDependency)
    val libraryDependency = moduleDependencyItem as ModuleDependencyItem.Exportable.LibraryDependency
    assertEquals(mavenLibraryName, libraryDependency.library.name)
  }

  @Test
  fun `test project library add to module disposed`() {
    val antLibraryName = "ant-lib"
    val library = createProjectLibrary(antLibraryName, withRoots = false)
    runWriteActionAndWait {
      val module = projectModel.createModule()
      ModuleRootManager.getInstance(module).modifiableModel.let { rootModel ->
        rootModel.addLibraryEntry(library)
        rootModel.dispose()
      }
      val moduleDependencies = WorkspaceModel.getInstance(project).entityStorage.current
        .entities(ModuleEntity::class.java).first()
        .dependencies
      assertEquals(1, moduleDependencies.size)
      assertTrue(moduleDependencies[0] is ModuleDependencyItem.ModuleSourceDependency)
    }
  }

  @Test
  fun `add remove excluded root`() {
    val library = createProjectLibrary("lib")
    assertEquals(2, events.size)
    assertEquals(0, excludedUrlEvents.size)
    
    val root = library.getFiles(OrderRootType.CLASSES).single()
    projectModel.modifyLibrary(library) {
      it.addExcludedRoot(root.url)
    }
    assertTrue(excludedUrlEvents.last() is EntityChange.Added)
    
    projectModel.modifyLibrary(library) {
      it.removeExcludedRoot(root.url)
    }
    assertEquals(2, excludedUrlEvents.size)
    assertTrue(excludedUrlEvents.last() is EntityChange.Removed)
  }

  private fun checkLibraryAddedEvent(event: EntityChange<LibraryEntity>, libraryName: String) {
    assertTrue(event is EntityChange.Added)
    val libraryEntity = (event as EntityChange.Added).entity
    assertEquals(libraryName, libraryEntity.name)
    assertTrue(libraryEntity.tableId is LibraryTableId.ProjectLibraryTableId)
    assertEquals(0, libraryEntity.roots.size)
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
  }
  
  private fun createProjectLibrary(libraryName: String, withRoots: Boolean = true): Library {
    val library = runWriteActionAndWait {
      LibraryTablesRegistrar.getInstance().getLibraryTable(project).createLibrary(libraryName)
    }
    if (withRoots) {
      projectModel.modifyLibrary(library) {
        it.addRoot(projectModel.baseProjectDir.newVirtualFile("$libraryName.jar"), OrderRootType.CLASSES)
        it.addRoot(projectModel.baseProjectDir.newVirtualFile("$libraryName-sources.jar"), OrderRootType.SOURCES)
      }
    }
    return library      
  }
}
