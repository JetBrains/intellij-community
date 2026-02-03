// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.*
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.testFramework.projectModel.library.MockCustomLibraryTableDescription
import com.intellij.platform.testFramework.projectModel.library.NewMockCustomLibraryTableDescription
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LegacyCustomLibraryEntitySource
import org.jdom.Element
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import javax.swing.JComponent
import kotlin.test.assertTrue

class CustomLibraryBridgeTest {
  @Rule
  @JvmField
  var application = ApplicationRule()

  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  @Test
  fun `test cleanup at custom library unloading`() {
    val disposable = Disposer.newDisposable()
    val libraryNames = mutableListOf("test-library", "test-mock-library")
    ExtensionPointName.create<CustomLibraryTableDescription>("com.intellij.customLibraryTable").point
      .registerExtension(MockCustomLibraryTableDescription(), disposable)

    val customLibraryTable = LibraryTablesRegistrar.getInstance().customLibraryTables.single()

    val application = ApplicationManager.getApplication()
    application.invokeAndWait {
      application.runWriteAction {
        libraryNames.forEach { libraryName ->
          customLibraryTable.createLibrary(libraryName)
        }
      }
    }

    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance(LocalEelMachine)
    globalWorkspaceModel.currentSnapshot.entities(LibraryEntity::class.java)
      .onEach { assertEquals(it.tableId, LibraryTableId.GlobalLibraryTableId("Mock")) }
      .onEach { assertTrue(libraryNames.contains(it.name)) }

    Disposer.dispose(disposable)

    application.invokeAndWait { UIUtil.dispatchAllInvocationEvents() }
    assertTrue(globalWorkspaceModel.currentSnapshot.entities (LibraryEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `test custom library serialization`() {
    ExtensionPointName.create<CustomLibraryTableDescription>("com.intellij.customLibraryTable").point
      .registerExtension(MockCustomLibraryTableDescription(), disposableRule.disposable)

    val customLibraryTable = LibraryTablesRegistrar.getInstance().customLibraryTables.single() as CustomLibraryTable

    val application = ApplicationManager.getApplication()
    application.invokeAndWait {
      application.runWriteAction {
        val modifiableTableModel = customLibraryTable.modifiableModel
        val library = modifiableTableModel.createLibrary("test-mock-library", MockLibraryType.KIND)
        val modifiableLibrary = library.modifiableModel as LibraryEx.ModifiableModelEx
        modifiableLibrary.properties = MockLibraryProperties("data")
        modifiableLibrary.addRoot("/a/b/c/d/", OrderRootType.CLASSES)
        modifiableLibrary.addRoot("/a/c/d", OrderRootType.SOURCES)
        modifiableLibrary.commit()
        modifiableTableModel.commit()
      }
    }

    val libraryTableElement = Element("libraryTable")
    customLibraryTable.writeExternal(libraryTableElement)
    assertEquals("""
      <libraryTable>
        <library name="test-mock-library" type="mock">
          <properties>
            <option name="data" value="data" />
          </properties>
          <CLASSES>
            <root url="/a/b/c/d/" />
          </CLASSES>
          <JAVADOC />
          <SOURCES>
            <root url="/a/c/d" />
          </SOURCES>
        </library>
      </libraryTable>
    """.trimIndent(), JDOMUtil.write(libraryTableElement))
  }

  @Test
  fun `test custom library loading`() {
    val application = ApplicationManager.getApplication()
    application.invokeAndWait { LibraryType.EP_NAME.point.registerExtension(MockLibraryType(), disposableRule.disposable) }

    ExtensionPointName.create<CustomLibraryTableDescription>("com.intellij.customLibraryTable").point
      .registerExtension(MockCustomLibraryTableDescription(), disposableRule.disposable)

    val newMockCustomLibraryTable = LibraryTablesRegistrar.getInstance().customLibraryTables.single() as CustomLibraryTable

    val libraryContentAsXml = """
      <libraryTable>
        <library name="test-mock-library" type="mock">
          <properties>
            <option name="data" value="data" />
          </properties>
          <CLASSES>
            <root url="/a/b/c/d/" />
          </CLASSES>
          <JAVADOC />
          <SOURCES>
            <root url="/a/c/d" />
          </SOURCES>
        </library>
      </libraryTable>
    """

    val element = JDOMUtil.load(libraryContentAsXml)
    newMockCustomLibraryTable.readExternal(element)

    application.invokeAndWait { UIUtil.dispatchAllInvocationEvents() }

    val library = newMockCustomLibraryTable.libraries.single() as LibraryEx
    assertEquals("test-mock-library", library.name)
    assertEquals("/a/b/c/d/", library.getUrls(OrderRootType.CLASSES)[0])
    assertEquals("/a/c/d", library.getUrls(OrderRootType.SOURCES)[0])
    assertEquals(MockLibraryProperties("data"), library.properties)
  }

  @Test
  fun `test two custom libraries with different name don't affect each other`() {
    checkCustomLibrariesNotAffectEachOther("test-library", "test-mock-library")
  }

  @Test
  fun `test two custom libraries with same name don't affect each other`() {
    checkCustomLibrariesNotAffectEachOther("test-library", "test-library")
  }

  private fun checkCustomLibrariesNotAffectEachOther(firstLibraryName: String, secondLibraryName: String) {
    val application = ApplicationManager.getApplication()
    val disposable = Disposer.newDisposable()

    // Crating custom libraries one by one
    val newMockCustomLibraryTableDescription = NewMockCustomLibraryTableDescription()
    ExtensionPointName.create<CustomLibraryTableDescription>("com.intellij.customLibraryTable").point
      .registerExtension(newMockCustomLibraryTableDescription, disposable)

    val customLibraryTable = LibraryTablesRegistrar.getInstance().customLibraryTables.single()
    application.invokeAndWait {
      application.runWriteAction {
        customLibraryTable.createLibrary(firstLibraryName)
      }
    }
    assertEquals(firstLibraryName, customLibraryTable.libraries.single().name)

    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance(LocalEelMachine)
    assertEquals(1, globalWorkspaceModel.currentSnapshot.entities(LibraryEntity::class.java).toList().size)

    val mockCustomLibraryTableDescription = MockCustomLibraryTableDescription()
    ExtensionPointName.create<CustomLibraryTableDescription>("com.intellij.customLibraryTable").point
      .registerExtension(mockCustomLibraryTableDescription, disposableRule.disposable)

    val newMockCustomLibraryTable = LibraryTablesRegistrar.getInstance().getCustomLibraryTableByLevel(mockCustomLibraryTableDescription.tableLevel) as CustomLibraryTable

    val libraryContentAsXml = """
      <libraryTable>
        <library name="${secondLibraryName}">
        </library>
      </libraryTable>
    """

    val element = JDOMUtil.load(libraryContentAsXml)
    // Check that loading for custom library don't affect others
    newMockCustomLibraryTable.readExternal(element)

    application.invokeAndWait { UIUtil.dispatchAllInvocationEvents() }
    assertEquals(secondLibraryName, newMockCustomLibraryTable.libraries.single().name)

    var libraryEntities = globalWorkspaceModel.currentSnapshot.entities(LibraryEntity::class.java)
    assertEquals(2, libraryEntities.toList().size)
    val expectedEntitySources = listOf(LegacyCustomLibraryEntitySource(newMockCustomLibraryTableDescription.tableLevel),
                                       LegacyCustomLibraryEntitySource(mockCustomLibraryTableDescription.tableLevel))
    assertEquals(expectedEntitySources, libraryEntities.map { it.entitySource }.toList())
    assertEquals(listOf(firstLibraryName, secondLibraryName).sorted(), libraryEntities.map { it.name }.toList().sorted())

    // Check that unloaded libraries were removed from the global storage
    Disposer.dispose(disposable)
    application.invokeAndWait { UIUtil.dispatchAllInvocationEvents() }

    libraryEntities = globalWorkspaceModel.currentSnapshot.entities(LibraryEntity::class.java)
    assertEquals(1, libraryEntities.toList().size)
    assertEquals(LegacyCustomLibraryEntitySource(mockCustomLibraryTableDescription.tableLevel),
                 libraryEntities.single().entitySource)
  }
}

private class MockLibraryProperties(var data: String = "default") : LibraryProperties<MockLibraryProperties>() {
  override fun getState(): MockLibraryProperties = this

  override fun loadState(state: MockLibraryProperties) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun equals(other: Any?): Boolean = (other as? MockLibraryProperties)?.data == data

  override fun hashCode(): Int = data.hashCode()
}

private class MockLibraryType : LibraryType<MockLibraryProperties>(KIND) {
  companion object {
    val KIND = object : PersistentLibraryKind<MockLibraryProperties>("mock") {
      override fun createDefaultProperties(): MockLibraryProperties = MockLibraryProperties()
    }
  }

  override fun getCreateActionName(): String? = null

  override fun createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile?, project: Project): NewLibraryConfiguration? {
    return null
  }

  override fun createPropertiesEditor(editorComponent: LibraryEditorComponent<MockLibraryProperties>): LibraryPropertiesEditor? {
    return null
  }
}