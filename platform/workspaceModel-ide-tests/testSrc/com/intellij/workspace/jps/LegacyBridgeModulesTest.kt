package com.intellij.workspace.jps

import com.intellij.configurationStore.StoreUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.rd.attach
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.*
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.*
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModule
import com.intellij.workspace.virtualFileUrl
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

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
    project = createEmptyTestProject(temporaryDirectoryRule, disposableRule)
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

      val moduleFile = module.moduleFile?.virtualFileUrl?.file
      assertNotNull(moduleFile)
      assertEquals(newNameFile, moduleFile)
      assertTrue(module.moduleFilePath.endsWith(newNameFile.name))

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
        assertNotNull(WorkspaceModel.getInstance(project).entityStore.current.entities(ModuleEntity::class.java)
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

      val moduleDirUrl = File(project.basePath).toVirtualFileUrl()
      val projectModel = WorkspaceModel.getInstance(project)

      projectModel.updateProjectModel {
        it.addModuleEntity("name", emptyList(), JpsFileEntitySource.FileInDirectory(moduleDirUrl, project.storagePlace!!))
      }

      assertNotNull(moduleManager.findModuleByName("name"))

      projectModel.updateProjectModel {
        val moduleEntity = it.entities(ModuleEntity::class.java).single()
        it.removeEntity(moduleEntity)
        it.addModuleEntity("name", emptyList(), JpsFileEntitySource.FileInDirectory(moduleDirUrl, project.storagePlace!!))
      }

      assertEquals(1, moduleManager.modules.size)
      assertNotNull(moduleManager.findModuleByName("name"))
    }

  @Test
  fun `test LibraryEntity is removed after removing module library order entry`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val module = moduleManager.modifiableModel.let {
        val m = it.newModule(File(project.basePath, "xxx.iml").path, EmptyModuleType.getInstance().id, null) as LegacyBridgeModule
        it.commit()
        m
      }

      ModuleRootModificationUtil.addModuleLibrary(module, "xxx-lib", listOf(File(project.basePath, "x.jar").path), emptyList())

      val projectModel = WorkspaceModel.getInstance(project)

      assertEquals(
        "xxx-lib",
        projectModel.entityStore.current.entities(LibraryEntity::class.java).toList().single().name)

      ModuleRootModificationUtil.updateModel(module) { model ->
        val orderEntry = model.orderEntries.filterIsInstance<LibraryOrderEntry>().single()
        model.removeOrderEntry(orderEntry)
      }

      assertEmpty(projectModel.entityStore.current.entities(LibraryEntity::class.java).toList())
    }

  @Test
  fun `test LibraryEntity is removed after clearing module order entries`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val module = moduleManager.modifiableModel.let {
        val m = it.newModule(File(project.basePath, "xxx.iml").path, EmptyModuleType.getInstance().id, null) as LegacyBridgeModule
        it.commit()
        m
      }

      ModuleRootModificationUtil.addModuleLibrary(module, "xxx-lib", listOf(File(project.basePath, "x.jar").path), emptyList())

      val projectModel = WorkspaceModel.getInstance(project)

      assertEquals(
        "xxx-lib",
        projectModel.entityStore.current.entities(LibraryEntity::class.java).toList().single().name)

      ModuleRootModificationUtil.updateModel(module) { model ->
        model.clear()
      }

      assertEmpty(projectModel.entityStore.current.entities(LibraryEntity::class.java).toList())
    }

  @Test
  fun `test content roots provided implicitly`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val dir = File(project.basePath, "dir")
      val moduleDirUrl = File(project.basePath).toVirtualFileUrl()
      val projectModel = WorkspaceModel.getInstance(project)

      val projectPlace = project.storagePlace!!

      projectModel.updateProjectModel {
        val moduleEntity = it.addModuleEntity("name", emptyList(), JpsFileEntitySource.FileInDirectory(moduleDirUrl, projectPlace))
        it.addSourceRootEntity(moduleEntity, dir.toVirtualFileUrl(), false, "", JpsFileEntitySource.FileInDirectory(moduleDirUrl, projectPlace))
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

    val modifiableRootModel = ModuleRootManager.getInstance(module).modifiableModel
    assertEquals(LanguageLevel.JDK_1_5, modifiableRootModel.getModuleExtension(TestModuleExtension::class.java).languageLevel)
    modifiableRootModel.dispose()

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

  @Test
  @RunsInEdt
  fun `test libraries are loaded from cache`() {
    val builder = TypedEntityStorageBuilder.create()

    val tempDir = temporaryDirectoryRule.newPath().toFile()

    val jarUrl = File(tempDir, "a.jar").toVirtualFileUrl()
    builder.addLibraryEntity(
      name = "my_lib",
      tableId = LibraryTableId.ProjectLibraryTableId,
      roots = listOf(LibraryRoot(jarUrl, LibraryRootTypeId("CLASSES"), LibraryRoot.InclusionOptions.ROOT_ITSELF)),
      excludedRoots = emptyList(),
      source = IdeUiEntitySource
    )

    WorkspaceModelInitialTestContent.withInitialContent(builder.toStorage()) {
      val project = ProjectManager.getInstance().createProject("testProject", File(tempDir, "testProject.ipr").path)!!
      ProjectManagerEx.getInstanceEx().openProject(project)
      disposableRule.disposable.attach { ProjectUtil.closeAndDispose(project) }

      val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
      val library = projectLibraryTable.getLibraryByName("my_lib")
      assertNotNull(library)

      assertEquals(JpsLibraryTableSerializer.PROJECT_LEVEL, library!!.table.tableLevel)
      assertSameElements(library.getUrls(OrderRootType.CLASSES), jarUrl.url)
    }
  }

  @Test
  @RunsInEdt
  fun `test custom source root loading`() {
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

      val customRoots = WorkspaceModel.getInstance(project).entityStore.current.entities(CustomSourceRootPropertiesEntity::class.java)
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
  @RunsInEdt
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

      assertSame(JavaSourceRootType.SOURCE, sourceFolder.rootType)
      assertTrue(sourceFolder.jpsElement.properties is JavaSourceRootProperties)

      val customRoot = WorkspaceModel.getInstance(project).entityStore.current.entities(CustomSourceRootPropertiesEntity::class.java)
        .toList().single()

      assertEquals("<sourceFolder param1=\"x y z\" />", customRoot.propertiesXmlTag)
      assertEquals("unsupported-custom-source-root-type", customRoot.sourceRoot.rootType)
    }
  }

  @Test
  @RunsInEdt
  fun `test custom source root saving`() {
    val tempDir = temporaryDirectoryRule.newPath().toFile()

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
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id, null) as LegacyBridgeModule
      moduleModel.commit()
      module
    }

    ModuleRootModificationUtil.updateModel(module) { model ->
      val tempDir = temporaryDirectoryRule.newPath().toFile()
      val url = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(tempDir.path))
      val contentEntry = model.addContentEntry(url)
      contentEntry.addSourceFolder("$url/$antLibraryFolder", TestCustomSourceRootType.INSTANCE)
    }
    StoreUtil.saveDocumentsAndProjectSettings(project)
    assertTrue(moduleFile.readText().contains(antLibraryFolder))
    val entityStore = WorkspaceModel.getInstance(project).entityStore
    assertEquals(1, entityStore.current.entities(ContentRootEntity::class.java).count())
    assertEquals(1, entityStore.current.entities(CustomSourceRootPropertiesEntity::class.java).count())

    ModuleManager.getInstance(project).disposeModule(module)
    assertEmpty(entityStore.current.entities(ContentRootEntity::class.java).toList())
    assertEmpty(entityStore.current.entities(CustomSourceRootPropertiesEntity::class.java).toList())
  }

  @Test
  fun `test remove content entity removes related source folders`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleName = "build"
    val antLibraryFolder = "ant-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")
    val module = ModuleManager.getInstance(project).modifiableModel.let { moduleModel ->
      val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id, null) as LegacyBridgeModule
      moduleModel.commit()
      module
    }

    ModuleRootModificationUtil.updateModel(module) { model ->
      val tempDir = temporaryDirectoryRule.newPath().toFile()
      val url = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(tempDir.path))
      val contentEntry = model.addContentEntry(url)
      contentEntry.addSourceFolder("$url/$antLibraryFolder", TestCustomSourceRootType.INSTANCE)
    }

    val entityStore = WorkspaceModel.getInstance(project).entityStore
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
}

internal fun createEmptyTestProject(temporaryDirectory: TemporaryDirectory,
                                    disposableRule: DisposableRule): Project {
  val projectDir = temporaryDirectory.newPath("project").toFile()
  val project = WorkspaceModelInitialTestContent.withInitialContent(TypedEntityStorageBuilder.create()) {
    ProjectManager.getInstance().createProject("testProject", File(projectDir, "testProject.ipr").path)!!
  }
  invokeAndWaitIfNeeded { ProjectManagerEx.getInstanceEx().openProject(project) }
  disposableRule.disposable.attach { invokeAndWaitIfNeeded { ProjectManagerEx.getInstanceEx().forceCloseProject(project) } }
  return project
}
