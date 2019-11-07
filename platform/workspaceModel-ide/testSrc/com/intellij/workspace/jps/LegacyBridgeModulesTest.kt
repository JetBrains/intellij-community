package com.intellij.workspace.jps

import com.intellij.configurationStore.StoreUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.rd.attach
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.*
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.*
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModule
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeProjectLifecycleListener
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class LegacyBridgeModulesTest {
  @Rule
  @JvmField
  var edtRule = EdtRule()

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
    val registryValue = Registry.get(LegacyBridgeProjectLifecycleListener.ENABLED_REGISTRY_KEY)
    val oldEnabledValue = registryValue.asBoolean()
    registryValue.setValue(true)
    disposableRule.disposable.attach { registryValue.setValue(oldEnabledValue) }

    val tempDir = temporaryDirectoryRule.newPath("project").toFile()
    project = ProjectManager.getInstance().createProject("testProject", File(tempDir, "testProject.ipr").path)!!
    runInEdt { ProjectManagerEx.getInstanceEx().openProject(project) }
    disposableRule.disposable.attach { runInEdt { ProjectUtil.closeAndDispose(project) } }
  }

  @Test
  fun `test that module continues to receive updates after being created in modifiable model`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val module = moduleManager.modifiableModel.let {
        val m = it.newModule(File(project.basePath, "xxx.iml").path, EmptyModuleType.getInstance().id, null) as LegacyBridgeModule
        it.commit()
        m
      }

      assertTrue(moduleManager.modules.contains(module))
      assertSame(WorkspaceModel.getInstance(project).entityStore, module.entityStore)

      val contentRootUrl = temporaryDirectoryRule.newPath("contentRoot").toVirtualFileUrl()

      WorkspaceModel.getInstance(project).updateProjectModel {
        val moduleEntity = it.resolve(module.moduleEntityId)!!
        it.addContentRootEntity(contentRootUrl, emptyList(), emptyList(), moduleEntity, moduleEntity.entitySource)
      }

      assertArrayEquals(
        ModuleRootManager.getInstance(module).contentRootUrls,
        arrayOf(contentRootUrl.url)
      )

      moduleManager.modifiableModel.let {
        it.disposeModule(module)
        it.commit()
      }
    }

  @Test
  fun `test commit module root model in uncommitted module`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val modulesModifiableModel = moduleManager.modifiableModel
      try {
        val m = modulesModifiableModel.newModule(File(project.basePath, "xxx.iml").path, ModuleType.EMPTY.id, null) as LegacyBridgeModule
        val rootModel = m.rootManager.modifiableModel

        val temp = temporaryDirectoryRule.newPath()
        rootModel.addContentEntry(temp.toVirtualFileUrl().url)
        rootModel.commit()

        assertArrayEquals(arrayOf(temp.toVirtualFileUrl().url), m.rootManager.contentRootUrls)
      } finally {
        modulesModifiableModel.dispose()
      }
    }

  @Test
  fun `test rename module from model`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val oldNameFile = File(project.basePath, "oldName.iml")
      val iprFile = File(project.projectFilePath!!)

      val module = moduleManager.modifiableModel.let { model ->
        val module = model.newModule(oldNameFile.path, ModuleType.EMPTY.id)
        model.commit()
        module
      }
      StoreUtil.saveDocumentsAndProjectSettings(project)
      assertTrue(oldNameFile.exists())
      assertTrue(iprFile.readText().contains(oldNameFile.name))

      assertEquals("oldName", module.name)

      moduleManager.modifiableModel.let { model ->
        assertSame(module, model.findModuleByName("oldName"))
        assertNull(model.getModuleToBeRenamed("oldName"))

        model.renameModule(module, "newName")

        assertSame(module, model.findModuleByName("oldName"))
        assertSame(module, model.getModuleToBeRenamed("newName"))
        assertSame("newName", model.getNewName(module))

        model.commit()
      }

      assertNull(moduleManager.findModuleByName("oldName"))
      assertSame(module, moduleManager.findModuleByName("newName"))
      assertEquals("newName", module.name)

      StoreUtil.saveDocumentsAndProjectSettings(project)

      val newNameFile = File(project.basePath, "newName.iml")

      assertFalse(iprFile.readText().contains(oldNameFile.name))
      assertFalse(oldNameFile.exists())

      assertTrue(iprFile.readText().contains(newNameFile.name))
      assertTrue(newNameFile.exists())
    }

  @Test
  fun `test remove and add module with the same name`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)
      for (module in moduleManager.modules) {
        moduleManager.disposeModule(module)
      }

      val modulePath = File(project.basePath, "oldName.iml").toVirtualFileUrl()
      val projectModel = WorkspaceModel.getInstance(project)

      projectModel.updateProjectModel {
        it.addModuleEntity("name", emptyList(), JpsFileEntitySource(modulePath, project.storagePlace!!))
      }

      assertNotNull(moduleManager.findModuleByName("name"))

      projectModel.updateProjectModel {
        val moduleEntity = it.entities(ModuleEntity::class).single()
        it.removeEntity(moduleEntity)
        it.addModuleEntity("name", emptyList(), JpsFileEntitySource(modulePath, project.storagePlace!!))
      }

      assertEquals(1, moduleManager.modules.size)
      assertNotNull(moduleManager.findModuleByName("name"))
    }

  @Test
  fun `test content roots provided implicitly`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val dir = File(project.basePath, "dir")
      val modulePath = File(project.basePath, "oldName.iml").toVirtualFileUrl()
      val projectModel = WorkspaceModel.getInstance(project)

      val projectPlace = project.storagePlace!!

      projectModel.updateProjectModel {
        val moduleEntity = it.addModuleEntity("name", emptyList(), JpsFileEntitySource(modulePath, projectPlace))
        it.addSourceRootEntity(moduleEntity, dir.toVirtualFileUrl(), false, "", JpsFileEntitySource(modulePath, projectPlace))
      }

      val module = moduleManager.findModuleByName("name")
      assertNotNull(module)

      assertArrayEquals(
        arrayOf(dir.toVirtualFileUrl().url),
        ModuleRootManager.getInstance(module!!).contentRootUrls
      )

      val sourceRootUrl = ModuleRootManager.getInstance(module).contentEntries.single()
        .sourceFolders.single().url
      assertEquals(dir.toVirtualFileUrl().url, sourceRootUrl)
    }

  @Test
  @RunsInEdt
  fun `test module component serialized into module iml`() {
    val moduleFile = File(project.basePath, "test.iml")

    val moduleManager = ModuleManager.getInstance(project)
    val module = WriteAction.computeAndWait<Module, Exception> { moduleManager.newModule (moduleFile.path, ModuleTypeId.JAVA_MODULE) }

    StoreUtil.saveDocumentsAndProjectSettings(project)

    assertEquals(
      "<module type=\"JAVA_MODULE\" version=\"4\">\n" +
      "  <component name=\"NewModuleRootManager\">\n" +
      "    <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n" +
      "  </component>\n" +
      "</module>",
      JDOMUtil.writeElement(JDOMUtil.load(moduleFile))
    )

    TestModuleComponent.getInstance(module).testString = "My String"

    StoreUtil.saveSettings(module, true)

    assertEquals(
      "<module type=\"JAVA_MODULE\" version=\"4\">\n" +
      "  <component name=\"NewModuleRootManager\">\n" +
      "    <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n" +
      "  </component>\n" +
      "  <component name=\"XXX\">\n" +
      "    <option name=\"testString\" value=\"My String\" />\n" +
      "  </component>\n" +
      "</module>",
      JDOMUtil.writeElement(JDOMUtil.load(moduleFile))
    )
  }

  @Test
  @RunsInEdt
  fun `test module extensions`() {
    TestModuleExtension.commitCalled.set(0)

    val module = ApplicationManager.getApplication().runWriteAction<Module> {
      ModuleManager.getInstance(project).newModule(File(project.basePath, "test.iml").path, ModuleType.EMPTY.id)
    }

    val modifiableModel = ApplicationManager.getApplication().runReadAction<ModifiableRootModel> { ModuleRootManager.getInstance(module).modifiableModel }
    val moduleExtension = modifiableModel.getModuleExtension(TestModuleExtension::class.java)
    moduleExtension.languageLevel = LanguageLevel.JDK_1_5
    ApplicationManager.getApplication().runWriteAction { modifiableModel.commit() }

    assertEquals(
      LanguageLevel.JDK_1_5,
      ModuleRootManager.getInstance(module).getModuleExtension(TestModuleExtension::class.java).languageLevel
    )

    assertEquals(
      LanguageLevel.JDK_1_5,
      ModuleRootManager.getInstance(module).modifiableModel.getModuleExtension(TestModuleExtension::class.java).languageLevel
    )

    assertEquals(1, TestModuleExtension.commitCalled.get())
  }

  @Test
  @RunsInEdt
  fun `test module libraries loaded from cache`() {
    val builder = TypedEntityStorageBuilder.create()

    val tempDir = temporaryDirectoryRule.newPath().toFile()

    val moduleEntity = builder.addModuleEntity(name = "test", dependencies = emptyList(), source = IdeUiEntitySource)
    val moduleLibraryEntity = builder.addLibraryEntity(
      name = "some",
      tableId = LibraryTableId.ModuleLibraryTableId(moduleEntity.persistentId()),
      roots = listOf(LibraryRoot(tempDir.toVirtualFileUrl(), LibraryRootTypeId("CLASSES"), LibraryRoot.InclusionOptions.ROOT_ITSELF)),
      excludedRoots = emptyList(),
      source = IdeUiEntitySource
    )
    builder.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      dependencies = listOf(ModuleDependencyItem.Exportable.LibraryDependency(
        moduleLibraryEntity.persistentId(), false, ModuleDependencyItem.DependencyScope.COMPILE))
    }

    WorkspaceModelInitialTestContent.withInitialContent(builder.toStorage()) {
      val project = ProjectManager.getInstance().createProject("testProject", File(tempDir, "testProject.ipr").path)!!
      ProjectManagerEx.getInstanceEx().openProject(project)
      disposableRule.disposable.attach { ProjectUtil.closeAndDispose(project) }

      val module = ModuleManager.getInstance(project).findModuleByName("test")

      val rootManager = ModuleRootManager.getInstance(module!!)
      val libraries = OrderEntryUtil.getModuleLibraries(rootManager)
      assertEquals(1, libraries.size)

      val libraryOrderEntry = rootManager.orderEntries[0] as LibraryOrderEntry
      assertTrue(libraryOrderEntry.isModuleLevel)
      assertSame(libraryOrderEntry.library, libraries[0])
      assertEquals(JpsLibraryTableSerializer.MODULE_LEVEL, libraryOrderEntry.libraryLevel)
      assertSameElements(libraryOrderEntry.getUrls(OrderRootType.CLASSES), tempDir.toVirtualFileUrl().url)
    }
  }
}