// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.ProjectTopics.PROJECT_ROOTS
import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.rd.attach
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.util.ui.UIUtil
import com.intellij.workspaceModel.ide.impl.WorkspaceModelInitialTestContent
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectEntitiesLoader
import com.intellij.workspaceModel.ide.impl.jps.serialization.toConfigLocation
import com.intellij.workspaceModel.ide.impl.toVirtualFileUrl
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.toVirtualFileUrl
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.module.UnknownSourceRootType
import org.jetbrains.jps.model.module.UnknownSourceRootTypeProperties
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModuleBridgesTest {
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
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun prepareProject() {
    project = createEmptyTestProject(temporaryDirectoryRule, disposableRule)
    virtualFileManager = VirtualFileUrlManager.getInstance(project)
  }

  @Test
  fun `test that module continues to receive updates after being created in modifiable model`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val module = moduleManager.modifiableModel.let {
        val m = it.newModule(File(project.basePath, "xxx.iml").path, EmptyModuleType.getInstance().id) as ModuleBridge
        it.commit()
        m
      }

      assertTrue(moduleManager.modules.contains(module))
      assertSame(WorkspaceModel.getInstance(project).entityStorage, module.entityStorage)

      val contentRootUrl = temporaryDirectoryRule.newPath("contentRoot").toVirtualFileUrl(virtualFileManager)

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
        val m = modulesModifiableModel.newModule(File(project.basePath, "xxx.iml").path, ModuleType.EMPTY.id) as ModuleBridge
        val rootModel = m.rootManager.modifiableModel

        val temp = temporaryDirectoryRule.newPath()
        rootModel.addContentEntry(temp.toVirtualFileUrl(virtualFileManager).url)
        rootModel.commit()

        assertArrayEquals(arrayOf(temp.toVirtualFileUrl(virtualFileManager).url), m.rootManager.contentRootUrls)
      } finally {
        modulesModifiableModel.dispose()
      }
    }

  @Test
  fun `test rename module from model`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val oldModuleName = "oldName"
      val newModuleName = "newName"
      val moduleManager = ModuleManager.getInstance(project)

      val iprFile = File(project.projectFilePath!!)
      val oldNameFile = File(project.basePath, "$oldModuleName.iml")
      val newNameFile = File(project.basePath, "$newModuleName.iml")

      val module = moduleManager.modifiableModel.let { model ->
        val module = model.newModule(oldNameFile.path, ModuleType.EMPTY.id)
        model.commit()
        module
      }
      StoreUtil.saveDocumentsAndProjectSettings(project)
      assertTrue(oldNameFile.exists())
      assertTrue(iprFile.readText().contains(oldNameFile.name))
      assertEquals(oldModuleName, module.name)

      moduleManager.modifiableModel.let { model ->
        assertSame(module, model.findModuleByName(oldModuleName))
        assertNull(model.getModuleToBeRenamed(oldModuleName))

        model.renameModule(module, newModuleName)

        assertSame(module, model.findModuleByName(oldModuleName))
        assertSame(module, model.getModuleToBeRenamed(newModuleName))
        assertSame(newModuleName, model.getNewName(module))

        model.commit()
      }

      assertNull(moduleManager.findModuleByName(oldModuleName))
      assertSame(module, moduleManager.findModuleByName(newModuleName))
      assertEquals(newModuleName, module.name)

      val moduleFile = module.moduleFile?.toVirtualFileUrl(virtualFileManager)?.file
      assertNotNull(moduleFile)
      assertEquals(newNameFile, moduleFile)
      assertTrue(module.getModuleNioFile().toString().endsWith(newNameFile.name))

      StoreUtil.saveDocumentsAndProjectSettings(project)

      assertFalse(iprFile.readText().contains(oldNameFile.name))
      assertFalse(oldNameFile.exists())

      assertTrue(iprFile.readText().contains(newNameFile.name))
      assertTrue(newNameFile.exists())
    }

  @Test
  fun `test rename module and all dependencies in other modules`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val checkModuleDependency = { moduleName: String, dependencyModuleName: String ->
        assertNotNull(WorkspaceModel.getInstance(project).entityStorage.current.entities(ModuleEntity::class.java)
                        .first { it.persistentId().name == moduleName }.dependencies
                        .find { it is ModuleDependencyItem.Exportable.ModuleDependency && it.module.name == dependencyModuleName })
      }

      val antModuleName = "ant"
      val mavenModuleName = "maven"
      val gradleModuleName = "gradle"
      val moduleManager = ModuleManager.getInstance(project)

      val iprFile = File(project.projectFilePath!!)
      val antModuleFile = File(project.basePath, "$antModuleName.iml")
      val mavenModuleFile = File(project.basePath, "$mavenModuleName.iml")
      val gradleModuleFile = File(project.basePath, "$gradleModuleName.iml")

      val (antModule, mavenModule) = moduleManager.modifiableModel.let { model ->
        val antModule = model.newModule(antModuleFile.path, ModuleType.EMPTY.id)
        val mavenModule = model.newModule(mavenModuleFile.path, ModuleType.EMPTY.id)
        model.commit()
        Pair(antModule, mavenModule)
      }
      ModuleRootModificationUtil.addDependency(mavenModule, antModule)
      checkModuleDependency(mavenModuleName, antModuleName)

      StoreUtil.saveDocumentsAndProjectSettings(project)
      var fileText = iprFile.readText()
      assertEquals(2, listOf(antModuleName, mavenModuleName).filter { fileText.contains(it) }.size)

      assertTrue(antModuleFile.exists())
      assertTrue(mavenModuleFile.exists())
      assertTrue(mavenModuleFile.readText().contains(antModuleName))

      moduleManager.modifiableModel.let { model ->
        model.renameModule(antModule, gradleModuleName)
        model.commit()
      }
      checkModuleDependency(mavenModuleName, gradleModuleName)

      StoreUtil.saveDocumentsAndProjectSettings(project)
      fileText = iprFile.readText()
      assertEquals(2, listOf(mavenModuleName, gradleModuleName).filter { fileText.contains(it) }.size)

      assertFalse(antModuleFile.exists())
      assertTrue(gradleModuleFile.exists())
      assertTrue(mavenModuleFile.exists())
      fileText = mavenModuleFile.readText()
      assertFalse(fileText.contains(antModuleName))
      assertFalse(fileText.contains(antModuleName))
    }

  @Test
  fun `test remove and add module with the same name`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)
      for (module in moduleManager.modules) {
        moduleManager.disposeModule(module)
      }

      val moduleDirUrl = virtualFileManager.fromPath(project.basePath!!)
      val projectModel = WorkspaceModel.getInstance(project)

      projectModel.updateProjectModel {
        it.addModuleEntity("name", emptyList(), JpsFileEntitySource.FileInDirectory(moduleDirUrl, project.configLocation!!))
      }

      assertNotNull(moduleManager.findModuleByName("name"))

      projectModel.updateProjectModel {
        val moduleEntity = it.entities(ModuleEntity::class.java).single()
        it.removeEntity(moduleEntity)
        it.addModuleEntity("name", emptyList(), JpsFileEntitySource.FileInDirectory(moduleDirUrl, project.configLocation!!))
      }

      assertEquals(1, moduleManager.modules.size)
      assertNotNull(moduleManager.findModuleByName("name"))
    }

  @Test
  fun `test LibraryEntity is removed after removing module library order entry`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val module = moduleManager.modifiableModel.let {
        val m = it.newModule(File(project.basePath, "xxx.iml").path, EmptyModuleType.getInstance().id) as ModuleBridge
        it.commit()
        m
      }

      ModuleRootModificationUtil.addModuleLibrary(module, "xxx-lib", listOf(File(project.basePath, "x.jar").path), emptyList())

      val projectModel = WorkspaceModel.getInstance(project)

      assertEquals(
        "xxx-lib",
        projectModel.entityStorage.current.entities(LibraryEntity::class.java).toList().single().name)

      ModuleRootModificationUtil.updateModel(module) { model ->
        val orderEntry = model.orderEntries.filterIsInstance<LibraryOrderEntry>().single()
        model.removeOrderEntry(orderEntry)
      }

      assertEmpty(projectModel.entityStorage.current.entities(LibraryEntity::class.java).toList())
    }

  @Test
  fun `test LibraryEntity is removed after clearing module order entries`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val module = moduleManager.modifiableModel.let {
        val m = it.newModule(File(project.basePath, "xxx.iml").path, EmptyModuleType.getInstance().id) as ModuleBridge
        it.commit()
        m
      }

      ModuleRootModificationUtil.addModuleLibrary(module, "xxx-lib", listOf(File(project.basePath, "x.jar").path), emptyList())

      val projectModel = WorkspaceModel.getInstance(project)

      assertEquals(
        "xxx-lib",
        projectModel.entityStorage.current.entities(LibraryEntity::class.java).toList().single().name)

      ModuleRootModificationUtil.updateModel(module) { model ->
        model.clear()
      }

      assertEmpty(projectModel.entityStorage.current.entities(LibraryEntity::class.java).toList())
    }

  @Test
  fun `test content roots provided implicitly`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val dir = File(project.basePath, "dir")
      val moduleDirUrl = virtualFileManager.fromPath(project.basePath!!)
      val projectModel = WorkspaceModel.getInstance(project)

      val projectLocation = project.configLocation!!

      projectModel.updateProjectModel {
        val moduleEntity = it.addModuleEntity("name", emptyList(), JpsFileEntitySource.FileInDirectory(moduleDirUrl, projectLocation))
        it.addSourceRootEntity(moduleEntity, dir.toVirtualFileUrl(virtualFileManager), false, "", JpsFileEntitySource.FileInDirectory(moduleDirUrl, projectLocation))
      }

      val module = moduleManager.findModuleByName("name")
      assertNotNull(module)

      assertArrayEquals(
        arrayOf(dir.toVirtualFileUrl(virtualFileManager).url),
        ModuleRootManager.getInstance(module!!).contentRootUrls
      )

      val sourceRootUrl = ModuleRootManager.getInstance(module).contentEntries.single()
        .sourceFolders.single().url
      assertEquals(dir.toVirtualFileUrl(virtualFileManager).url, sourceRootUrl)
    }

  @Test
  fun `test module component serialized into module iml`() {
    val moduleFile = File(project.basePath, "test.iml")

    val moduleManager = ModuleManager.getInstance(project)
    val module = runWriteActionAndWait { moduleManager.newModule (moduleFile.path, ModuleTypeId.JAVA_MODULE) }

    runWriteActionAndWait { StoreUtil.saveDocumentsAndProjectSettings(project) }

    assertNull(JDomSerializationUtil.findComponent(JDOMUtil.load(moduleFile), "XXX"))

    TestModuleComponent.getInstance(module).testString = "My String"

    StoreUtil.saveSettings(module, true)

    assertEquals(
      """  
       <component name="XXX">
         <option name="testString" value="My String" />
       </component>
""".trimIndent(), JDOMUtil.write(JDomSerializationUtil.findComponent(JDOMUtil.load(moduleFile), "XXX")!!)
    )
  }

  @Test
  fun `test module extensions`() {
    TestModuleExtension.commitCalled.set(0)

    val module = runWriteActionAndWait {
      ModuleManager.getInstance(project).newModule(File(project.basePath, "test.iml").path, ModuleType.EMPTY.id)
    }

    val modifiableModel = ApplicationManager.getApplication().runReadAction<ModifiableRootModel> { ModuleRootManager.getInstance(module).modifiableModel }
    val moduleExtension = modifiableModel.getModuleExtension(TestModuleExtension::class.java)
    moduleExtension.languageLevel = LanguageLevel.JDK_1_5
    runWriteActionAndWait { modifiableModel.commit() }

    assertEquals(
      LanguageLevel.JDK_1_5,
      ModuleRootManager.getInstance(module).getModuleExtension(TestModuleExtension::class.java).languageLevel
    )

    val modifiableRootModel = ModuleRootManager.getInstance(module).modifiableModel
    assertEquals(LanguageLevel.JDK_1_5, modifiableRootModel.getModuleExtension(TestModuleExtension::class.java).languageLevel)
    modifiableRootModel.dispose()

    assertEquals(1, TestModuleExtension.commitCalled.get())
  }

  @Test
  fun `test module libraries loaded from cache`() {
    val builder = WorkspaceEntityStorageBuilder.create()

    val tempDir = temporaryDirectoryRule.newPath()

    val iprFile = tempDir.resolve("testProject.ipr")
    val configLocation = toConfigLocation(iprFile, virtualFileManager)
    val source = JpsFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)
    val moduleEntity = builder.addModuleEntity(name = "test", dependencies = emptyList(), source = source)
    val moduleLibraryEntity = builder.addLibraryEntity(
      name = "some",
      tableId = LibraryTableId.ModuleLibraryTableId(moduleEntity.persistentId()),
      roots = listOf(LibraryRoot(tempDir.toVirtualFileUrl(virtualFileManager), LibraryRootTypeId("CLASSES"), LibraryRoot.InclusionOptions.ROOT_ITSELF)),
      excludedRoots = emptyList(),
      source = source
    )
    builder.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      dependencies = listOf(
        ModuleDependencyItem.Exportable.LibraryDependency(moduleLibraryEntity.persistentId(), false, ModuleDependencyItem.DependencyScope.COMPILE)
      )
    }

    WorkspaceModelInitialTestContent.withInitialContent(builder.toStorage()) {
      val project = PlatformTestUtil.loadAndOpenProject(iprFile)
      Disposer.register(disposableRule.disposable, Disposable {
        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      })

      val module = ModuleManager.getInstance(project).findModuleByName("test")

      val rootManager = ModuleRootManager.getInstance(module!!)
      val libraries = OrderEntryUtil.getModuleLibraries(rootManager)
      assertEquals(1, libraries.size)

      val libraryOrderEntry = rootManager.orderEntries[0] as LibraryOrderEntry
      assertTrue(libraryOrderEntry.isModuleLevel)
      assertSame(libraryOrderEntry.library, libraries[0])
      assertEquals(JpsLibraryTableSerializer.MODULE_LEVEL, libraryOrderEntry.libraryLevel)
      assertSameElements(libraryOrderEntry.getUrls(OrderRootType.CLASSES), tempDir.toVirtualFileUrl(virtualFileManager).url)
    }
  }

  @Test
  fun `test libraries are loaded from cache`() {
    val builder = WorkspaceEntityStorageBuilder.create()

    val tempDir = temporaryDirectoryRule.newPath()

    val iprFile = tempDir.resolve("testProject.ipr")
    val jarUrl = tempDir.resolve("a.jar").toVirtualFileUrl(virtualFileManager)
    builder.addLibraryEntity(
      name = "my_lib",
      tableId = LibraryTableId.ProjectLibraryTableId,
      roots = listOf(LibraryRoot(jarUrl, LibraryRootTypeId("CLASSES"), LibraryRoot.InclusionOptions.ROOT_ITSELF)),
      excludedRoots = emptyList(),
      source = JpsProjectEntitiesLoader.createJpsEntitySourceForProjectLibrary(toConfigLocation(iprFile, virtualFileManager))
    )

    WorkspaceModelInitialTestContent.withInitialContent(builder.toStorage()) {
      val project = PlatformTestUtil.loadAndOpenProject(iprFile)
      Disposer.register(disposableRule.disposable, Disposable {
        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      })

      val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
      invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }

      val library = projectLibraryTable.getLibraryByName("my_lib")
      assertNotNull(library)

      assertEquals(JpsLibraryTableSerializer.PROJECT_LEVEL, library!!.table.tableLevel)
      assertSameElements(library.getUrls(OrderRootType.CLASSES), jarUrl.url)
    }
  }

  @Test
  fun `test custom source root loading`() {
    TestCustomRootModelSerializerExtension.registerTestCustomSourceRootType(temporaryDirectoryRule.newPath().toFile(),
                                                                            disposableRule.disposable)
    val tempDir = temporaryDirectoryRule.newPath().toFile()
    val moduleImlFile = File(tempDir, "my.iml")
    Files.createDirectories(moduleImlFile.parentFile.toPath())
    val url = VfsUtilCore.pathToUrl(moduleImlFile.parentFile.path)
    moduleImlFile.writeText("""
      <module type="JAVA_MODULE" version="4">
        <component name="NewModuleRootManager">
          <content url="file://${'$'}MODULE_DIR${'$'}">
            <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/root1" type="custom-source-root-type" testString="x y z" />
            <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/root2" type="custom-source-root-type" />
          </content>
        </component>
      </module>
    """.trimIndent())

    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)
      val module = moduleManager.loadModule(moduleImlFile.path)
      val contentEntry = ModuleRootManager.getInstance(module).contentEntries.single()

      assertEquals(2, contentEntry.sourceFolders.size)

      assertTrue(contentEntry.sourceFolders[0].rootType is TestCustomSourceRootType)
      assertEquals("x y z", (contentEntry.sourceFolders[0].jpsElement.properties as TestCustomSourceRootProperties).testString)

      assertTrue(contentEntry.sourceFolders[1].rootType is TestCustomSourceRootType)
      assertEquals(null, (contentEntry.sourceFolders[1].jpsElement.properties as TestCustomSourceRootProperties).testString)

      val customRoots = WorkspaceModel.getInstance(project).entityStorage.current.entities(CustomSourceRootPropertiesEntity::class.java)
        .toList()
        .sortedBy { it.sourceRoot.url.url }
      assertEquals(2, customRoots.size)

      assertEquals("<sourceFolder testString=\"x y z\" />", customRoots[0].propertiesXmlTag)
      assertEquals("$url/root1", customRoots[0].sourceRoot.url.url)
      assertEquals(TestCustomSourceRootType.TYPE_ID, customRoots[0].sourceRoot.rootType)

      assertEquals("<sourceFolder />", customRoots[1].propertiesXmlTag)
      assertEquals("$url/root2", customRoots[1].sourceRoot.url.url)
      assertEquals(TestCustomSourceRootType.TYPE_ID, customRoots[1].sourceRoot.rootType)
    }
  }

  @Test
  fun `test unknown custom source root type`() {
    val tempDir = temporaryDirectoryRule.newPath().toFile()
    val moduleImlFile = File(tempDir, "my.iml")
    Files.createDirectories(moduleImlFile.parentFile.toPath())
    moduleImlFile.writeText("""
      <module type="JAVA_MODULE" version="4">
        <component name="NewModuleRootManager">
          <content url="file://${'$'}MODULE_DIR${'$'}">
            <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/root1" type="unsupported-custom-source-root-type" param1="x y z" />
          </content>
        </component>
      </module>
    """.trimIndent())

    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)
      val module = moduleManager.loadModule(moduleImlFile.path)
      val contentEntry = ModuleRootManager.getInstance(module).contentEntries.single()
      val sourceFolder = contentEntry.sourceFolders.single()

      assertSame(UnknownSourceRootType.getInstance("unsupported-custom-source-root-type"), sourceFolder.rootType)
      assertTrue(sourceFolder.jpsElement.properties is UnknownSourceRootTypeProperties<*>)

      val customRoot = WorkspaceModel.getInstance(project).entityStorage.current.entities(CustomSourceRootPropertiesEntity::class.java)
        .toList().single()

      assertEquals("<sourceFolder param1=\"x y z\" />", customRoot.propertiesXmlTag)
      assertEquals("unsupported-custom-source-root-type", customRoot.sourceRoot.rootType)
    }
  }

  @Test
  fun `test custom source root saving`() {
    val tempDir = temporaryDirectoryRule.newPath().toFile()
    TestCustomRootModelSerializerExtension.registerTestCustomSourceRootType(temporaryDirectoryRule.newPath().toFile(), disposableRule.disposable)

    val moduleImlFile = File(tempDir, "my.iml")
    Files.createDirectories(moduleImlFile.parentFile.toPath())

    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val module = moduleManager.newModule(moduleImlFile.path, ModuleTypeId.WEB_MODULE)
      ModuleRootModificationUtil.updateModel(module) { model ->
        val url = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(tempDir.path))
        val contentEntry = model.addContentEntry(url)
        contentEntry.addSourceFolder("$url/root1", TestCustomSourceRootType.INSTANCE)
        contentEntry.addSourceFolder("$url/root2", TestCustomSourceRootType.INSTANCE, TestCustomSourceRootProperties(""))
        contentEntry.addSourceFolder("$url/root3", TestCustomSourceRootType.INSTANCE, TestCustomSourceRootProperties("some data"))
        contentEntry.addSourceFolder("$url/root4", TestCustomSourceRootType.INSTANCE, TestCustomSourceRootProperties(null))
      }

      StoreUtil.saveDocumentsAndProjectSettings(project)

      val rootManagerComponent = JDomSerializationUtil.findComponent(JDOMUtil.load(moduleImlFile), "NewModuleRootManager")!!
      assertEquals("""
            <content url="file://${'$'}MODULE_DIR${'$'}">
              <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/root1" type="custom-source-root-type" testString="default properties" />
              <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/root2" type="custom-source-root-type" testString="" />
              <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/root3" type="custom-source-root-type" testString="some data" />
              <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/root4" type="custom-source-root-type" />
            </content>
      """.trimIndent(), JDOMUtil.write(rootManagerComponent.getChild("content")!!))
    }
  }

  @Test
  fun `test remove module removes source roots`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryFolder = "ant-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
      moduleModel.commit()
      module
    }

    ModuleRootModificationUtil.updateModel(module) { model ->
      val tempDir = temporaryDirectoryRule.newPath().toFile()
      val url = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(tempDir.path))
      val contentEntry = model.addContentEntry(url)
      contentEntry.addSourceFolder("$url/$antLibraryFolder", false)
    }
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(moduleFile.readText().contains(antLibraryFolder))
    val entityStore = WorkspaceModel.getInstance(project).entityStorage
    assertEquals(1, entityStore.current.entities(ContentRootEntity::class.java).count())
    assertEquals(1, entityStore.current.entities(JavaSourceRootEntity::class.java).count())

    ModuleManager.getInstance(project).disposeModule(module)
    assertEmpty(entityStore.current.entities(ContentRootEntity::class.java).toList())
    assertEmpty(entityStore.current.entities(JavaSourceRootEntity::class.java).toList())
  }

  @Test
  fun `test remove content entity removes related source folders`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryFolder = "ant-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
      moduleModel.commit()
      module
    }

    ModuleRootModificationUtil.updateModel(module) { model ->
      val tempDir = temporaryDirectoryRule.newPath().toFile()
      val url = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(tempDir.path))
      val contentEntry = model.addContentEntry(url)
      contentEntry.addSourceFolder("$url/$antLibraryFolder", false)
    }

    val entityStore = WorkspaceModel.getInstance(project).entityStorage
    assertEquals(1, entityStore.current.entities(ContentRootEntity::class.java).count())
    assertEquals(1, entityStore.current.entities(SourceRootEntity::class.java).count())

    ModuleRootModificationUtil.updateModel(module) { model ->
      val contentEntries = model.contentEntries
      assertEquals(1, contentEntries.size)
      model.removeContentEntry(contentEntries[0])
    }

    assertEmpty(entityStore.current.entities(ContentRootEntity::class.java).toList())
    assertEmpty(entityStore.current.entities(SourceRootEntity::class.java).toList())
  }

  @Test
  fun `test disposed module doesn't appear in rootsChanged`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
      moduleModel.commit()
      module
    }

    project.messageBus.connect(disposableRule.disposable).subscribe(PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        val modules = ModuleManager.getInstance(event.project).modules
        assertEmpty(modules)
      }
    })

    ModuleManager.getInstance(project).disposeModule(module)
  }
}

internal fun createEmptyTestProject(temporaryDirectory: TemporaryDirectory, disposableRule: DisposableRule): Project {
  val projectDir = temporaryDirectory.newPath("project")
  val project = WorkspaceModelInitialTestContent.withInitialContent(WorkspaceEntityStorageBuilder.create()) {
    PlatformTestUtil.loadAndOpenProject(projectDir.resolve("testProject.ipr"))
  }
  disposableRule.disposable.attach { PlatformTestUtil.forceCloseProjectWithoutSaving(project) }
  return project
}
