package com.intellij.workspace.jps

import com.intellij.configurationStore.StoreUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.rd.attach
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.IdeUiEntitySource
import com.intellij.workspace.ide.JpsFileEntitySource
import com.intellij.workspace.ide.storagePlace
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModule
import com.intellij.workspace.legacyBridge.intellij.ProjectModel
import com.intellij.workspace.legacyBridge.intellij.ProjectModelInitialTestContent
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.junit.Assert
import org.junit.Test
import java.io.File

class LegacyBridgeModulesTest : HeavyPlatformTestCase() {
  @Test
  fun `test that module continues to receive updates after being created in modifiable model`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val module = moduleManager.modifiableModel.let {
        val m = it.newModule("/xxx", moduleType.id, null) as LegacyBridgeModule
        it.commit()
        m
      }

      Assert.assertTrue(moduleManager.modules.contains(module))
      Assert.assertSame(ProjectModel.getInstance(project).entityStore, module.entityStore)

      val contentRootUrl = createTempDir("contentRoot").toVirtualFileUrl()

      ProjectModel.getInstance(project).updateProjectModel {
        val moduleEntity = it.resolve(module.moduleEntityId)!!
        it.addContentRootEntity(contentRootUrl, emptyList(), emptyList(), moduleEntity, moduleEntity.entitySource)
      }

      Assert.assertArrayEquals(
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
        val m = modulesModifiableModel.newModule("/xxx", moduleType.id, null) as LegacyBridgeModule
        val rootModel = m.rootManager.modifiableModel

        val temp = createTempDirectory()
        rootModel.addContentEntry(temp.toVirtualFileUrl().url)
        rootModel.commit()

        Assert.assertArrayEquals(arrayOf(temp.toVirtualFileUrl().url), m.rootManager.contentRootUrls)
      } finally {
        modulesModifiableModel.dispose()
      }
    }

  // TODO Check the iml file was renamed on disk after fixing module storages
  @Test
  fun `test rename module from model`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val module = moduleManager.modifiableModel.let { model ->
        val module = model.newModule(File(myProject.basePath, "oldName.iml").path, moduleType.id)
        model.commit()
        module
      }

      Assert.assertEquals("oldName", module.name)

      moduleManager.modifiableModel.let { model ->
        Assert.assertSame(module, model.findModuleByName("oldName"))
        Assert.assertNull(model.getModuleToBeRenamed("oldName"))

        model.renameModule(module, "newName")

        Assert.assertSame(module, model.findModuleByName("oldName"))
        Assert.assertSame(module, model.getModuleToBeRenamed("newName"))
        Assert.assertSame("newName", model.getNewName(module))

        model.commit()
      }

      Assert.assertNull(moduleManager.findModuleByName("oldName"))
      Assert.assertSame(module, moduleManager.findModuleByName("newName"))
      Assert.assertEquals("newName", module.name)
    }

  @Test
  fun `test remove and add module with the same name`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)
      for (module in moduleManager.modules) {
        moduleManager.disposeModule(module)
      }

      val modulePath = File(myProject.basePath, "oldName.iml").toVirtualFileUrl()
      val projectModel = ProjectModel.getInstance(project)

      projectModel.updateProjectModel {
        it.addModuleEntity("name", emptyList(), JpsFileEntitySource(modulePath, project.storagePlace!!))
      }

      Assert.assertNotNull(moduleManager.findModuleByName("name"))

      projectModel.updateProjectModel {
        val moduleEntity = it.entities(ModuleEntity::class).single()
        it.removeEntity(moduleEntity)
        it.addModuleEntity("name", emptyList(), JpsFileEntitySource(modulePath, project.storagePlace!!))
      }

      Assert.assertEquals(1, moduleManager.modules.size)
      Assert.assertNotNull(moduleManager.findModuleByName("name"))
    }

  @Test
  fun `test content roots provided implicitly`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val dir = File(myProject.basePath, "dir")
      val modulePath = File(myProject.basePath, "oldName.iml").toVirtualFileUrl()
      val projectModel = ProjectModel.getInstance(project)

      val projectPlace = myProject.storagePlace!!

      projectModel.updateProjectModel {
        val moduleEntity = it.addModuleEntity("name", emptyList(), JpsFileEntitySource(modulePath, projectPlace))
        it.addSourceRootEntity(moduleEntity, dir.toVirtualFileUrl(), false, "", JpsFileEntitySource(modulePath, projectPlace))
      }

      val module = moduleManager.findModuleByName("name")
      Assert.assertNotNull(module)

      Assert.assertArrayEquals(
        arrayOf(dir.toVirtualFileUrl().url),
        ModuleRootManager.getInstance(module!!).contentRootUrls
      )

      val sourceRootUrl = ModuleRootManager.getInstance(module).contentEntries.single()
        .sourceFolders.single().url
      Assert.assertEquals(dir.toVirtualFileUrl().url, sourceRootUrl)
    }

  @Test
  fun `test module component serialized into module iml`() {
    val moduleFile = File(myProject.basePath, "test.iml")

    val moduleManager = ModuleManager.getInstance(project)
    val module = WriteAction.computeAndWait<Module, Exception> { moduleManager.newModule (moduleFile.path, ModuleTypeId.JAVA_MODULE) }

    StoreUtil.saveDocumentsAndProjectSettings(myProject)

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

  fun `test module extensions`() {
    TestModuleExtension.commitCalled.set(0)

    val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
    val moduleExtension = modifiableModel.getModuleExtension(TestModuleExtension::class.java)
    moduleExtension.languageLevel = LanguageLevel.JDK_1_5
    ApplicationManager.getApplication().runWriteAction { modifiableModel.commit() }

    Assert.assertEquals(
      LanguageLevel.JDK_1_5,
      ModuleRootManager.getInstance(module).getModuleExtension(TestModuleExtension::class.java).languageLevel
    )

    Assert.assertEquals(
      LanguageLevel.JDK_1_5,
      ModuleRootManager.getInstance(module).modifiableModel.getModuleExtension(TestModuleExtension::class.java).languageLevel
    )

    Assert.assertEquals(1, TestModuleExtension.commitCalled.get())
  }

  fun `test module libraries loaded from cache`() {
    val builder = TypedEntityStorageBuilder.create()

    val tempDir = this.createTempDirectory()

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

    ProjectModelInitialTestContent.withInitialContent(builder.toStorage()) {
      val project = ProjectManager.getInstance().createProject("testProject", File(tempDir, "testProject.ipr").path)!!
      ProjectManagerEx.getInstanceEx().openProject(project)
      testRootDisposable.attach { ProjectUtil.closeAndDispose(project) }

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