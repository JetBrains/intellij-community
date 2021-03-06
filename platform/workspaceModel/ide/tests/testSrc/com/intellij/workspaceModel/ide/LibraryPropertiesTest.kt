// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryPropertiesEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class LibraryPropertiesTest {
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
  private val antLibraryName = "ant-lib"
  private val kindId = "testLibraryProperties"
  private val propertyData = "ant-lib-property"
  private val libraryProperty = object : PersistentLibraryKind<TestLibraryProperties>(kindId) {
    override fun createDefaultProperties(): TestLibraryProperties = TestLibraryProperties(propertyData)
  }

  @Before
  fun prepareProject() {
    project = createEmptyTestProject(temporaryDirectoryRule, disposableRule)
  }

  @After
  fun tearDown() {
    PersistentLibraryKind.unregisterKind(libraryProperty)
  }

  @Test
  fun `test project library properties save`() {
    val elementAsString = "<properties data=\"$propertyData\" />"

    WriteCommandAction.runWriteCommandAction(project) {
      val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
      projectLibraryTable.modifiableModel.let { projectLibTableModel ->
        projectLibTableModel.createLibrary(antLibraryName, libraryProperty)
        projectLibTableModel.commit()
      }

      WorkspaceModel.getInstance(project).entityStorage.current.entities(LibraryPropertiesEntity::class.java).forEach {
        assertEquals(kindId, it.libraryType)
        assertEquals(elementAsString, it.propertiesXmlTag)
      }

      projectLibraryTable.libraryIterator.forEach {
        assertEquals(propertyData, ((it as LibraryEx).properties as TestLibraryProperties).data)
      }
    }
  }

  @Test
  fun `test module library properties save`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val elementAsString = "<properties data=\"$propertyData\" />"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
      moduleModel.commit()
      module
    }

    val moduleRootManager = ModuleRootManager.getInstance(module)
    moduleRootManager.modifiableModel.let { rootModel ->
      rootModel.moduleLibraryTable.modifiableModel.let {
        it.createLibrary(antLibraryName, libraryProperty)
        it.commit()
      }
      rootModel.commit()
    }

    WorkspaceModel.getInstance(project).entityStorage.current.entities(LibraryPropertiesEntity::class.java).forEach {
      assertEquals(antLibraryName, it.library.name)
      assertEquals(kindId, it.libraryType)
      assertEquals(elementAsString, it.propertiesXmlTag)
    }

    moduleRootManager.modifiableModel.let { rootModel ->
      rootModel.moduleLibraryTable.libraryIterator.forEach {
        assertEquals(antLibraryName, it.name)
        assertEquals(propertyData, ((it as LibraryEx).properties as TestLibraryProperties).data)
      }
      rootModel.dispose()
    }
  }
}

private class TestLibraryProperties(@get:Attribute("data") var data: String = "") : LibraryProperties<TestLibraryProperties>() {
  override fun getState(): TestLibraryProperties? = this

  override fun loadState(state: TestLibraryProperties) {
    data = state.data
  }

  override fun equals(other: Any?): Boolean = (other as? TestLibraryProperties)?.data == data

  override fun hashCode(): Int = data.hashCode()
}