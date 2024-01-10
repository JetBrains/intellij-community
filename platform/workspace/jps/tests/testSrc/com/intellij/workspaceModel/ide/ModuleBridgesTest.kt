// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.openapi.application.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.*
import com.intellij.platform.workspace.jps.JpsEntitySourceFactory
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.project.stateStore
import com.intellij.testFramework.*
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.util.io.write
import com.intellij.util.ui.UIUtil
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelInitialTestContent
import com.intellij.workspaceModel.ide.impl.jps.serialization.toConfigLocation
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModuleEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.module.UnknownSourceRootType
import org.jetbrains.jps.model.module.UnknownSourceRootTypeProperties
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.junit.Assert.*
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.random.Random

class ModuleBridgesTest {
  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  private val temporaryDirectoryRule: TempDirectory
    get() = projectModel.baseProjectDir

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var project: Project
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun prepareProject() {
    project = projectModel.project
    virtualFileManager = VirtualFileUrlManager.getInstance(project)
  }

  @Test
  fun `test that module continues to receive updates after being created in modifiable model`() {
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val module = projectModel.createModule() as ModuleBridge

      assertTrue(moduleManager.modules.contains(module))
      assertSame((WorkspaceModel.getInstance(project) as WorkspaceModelImpl).entityStorage, module.entityStorage)

      val contentRootUrl = temporaryDirectoryRule.newDirectoryPath("contentRoot").toVirtualFileUrl(virtualFileManager)

      WorkspaceModel.getInstance(project).updateProjectModel {
        val moduleEntity = module.findModuleEntity(it)!!
        it addEntity ContentRootEntity(contentRootUrl, emptyList<@NlsSafe String>(), moduleEntity.entitySource) {
          this@ContentRootEntity.module = moduleEntity
        }
      }

      assertArrayEquals(
        ModuleRootManager.getInstance(module).contentRootUrls,
        arrayOf(contentRootUrl.url)
      )

      moduleManager.getModifiableModel().let {
        it.disposeModule(module)
        it.commit()
      }
    }
  }

  @Test
  fun `test commit module root model in uncommitted module`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val modulesModifiableModel = moduleManager.getModifiableModel()
      try {
        val m = projectModel.createModule("xxx", modulesModifiableModel)
        val rootModel = m.rootManager.modifiableModel

        val temp = temporaryDirectoryRule.newDirectoryPath()
        rootModel.addContentEntry(temp.toVirtualFileUrl(virtualFileManager).url)
        rootModel.commit()

        assertArrayEquals(arrayOf(temp.toVirtualFileUrl(virtualFileManager).url), m.rootManager.contentRootUrls)
      }
      finally {
        modulesModifiableModel.dispose()
      }
    }

  @Test
  fun `test rename module from model`() {
    val oldModuleName = "oldName"
    val newModuleName = "newName"
    val moduleManager = ModuleManager.getInstance(project)

    val modulesXmlFile = File(project.basePath, ".idea/modules.xml")
    val oldNameFile = File(project.basePath, "$oldModuleName.iml")
    val newNameFile = File(project.basePath, "$newModuleName.iml")

    runBlocking {
      val module = withContext(Dispatchers.EDT) {
        runWriteAction {
          val model = moduleManager.getModifiableModel()
          val module = model.newModule(oldNameFile.path, ModuleType.EMPTY.id)
          model.commit()
          module
        }
      }

      project.stateStore.save()
      project.stateStore.save()

      assertTrue(oldNameFile.exists())
      assertTrue(modulesXmlFile.readText().contains(oldNameFile.name))
      assertEquals(oldModuleName, module.name)


      withContext(Dispatchers.EDT) {
        ApplicationManager.getApplication().runWriteAction {
          val model = moduleManager.getModifiableModel()
          assertSame(module, model.findModuleByName(oldModuleName))
          assertNull(model.getModuleToBeRenamed(oldModuleName))

          model.renameModule(module, newModuleName)

          assertSame(module, model.findModuleByName(oldModuleName))
          assertSame(module, model.getModuleToBeRenamed(newModuleName))
          assertSame(newModuleName, model.getNewName(module))

          model.commit()
        }
      }

      assertNull(moduleManager.findModuleByName(oldModuleName))
      assertSame(module, moduleManager.findModuleByName(newModuleName))
      assertEquals(newModuleName, module.name)

      val moduleFilePath = module.moduleFile?.toVirtualFileUrl(virtualFileManager)?.presentableUrl
      assertNotNull(moduleFilePath)
      assertEquals(newNameFile, File(moduleFilePath!!))
      assertTrue(module.getModuleNioFile().toString().endsWith(newNameFile.name))

      project.stateStore.save()

      assertFalse(modulesXmlFile.readText().contains(oldNameFile.name))
      assertFalse(oldNameFile.exists())

      assertTrue(modulesXmlFile.readText().contains(newNameFile.name))
      assertTrue(newNameFile.exists())
    }
  }

  @Test
  fun `test rename module and all dependencies in other modules`() = runBlocking {
    val checkModuleDependency = { moduleName: String, dependencyModuleName: String ->
      assertNotNull(WorkspaceModel.getInstance(project).currentSnapshot.entities(ModuleEntity::class.java)
                      .first { it.symbolicId.name == moduleName }.dependencies
                      .find { it is ModuleDependencyItem.Exportable.ModuleDependency && it.module.name == dependencyModuleName })
    }

    val antModuleName = "ant"
    val mavenModuleName = "maven"
    val gradleModuleName = "gradle"
    val moduleManager = ModuleManager.getInstance(project)

    val modulesFile = File(project.basePath, ".idea/modules.xml")
    val antModuleFile = File(project.basePath, "$antModuleName.iml")
    val mavenModuleFile = File(project.basePath, "$mavenModuleName.iml")
    val gradleModuleFile = File(project.basePath, "$gradleModuleName.iml")

    val (antModule, mavenModule) = withContext(Dispatchers.EDT) {
      runWriteAction {
        val model = moduleManager.getModifiableModel()
        val antModule = model.newModule(antModuleFile.path, ModuleType.EMPTY.id)
        val mavenModule = model.newModule(mavenModuleFile.path, ModuleType.EMPTY.id)
        model.commit()
        Pair(antModule, mavenModule)
      }
    }
    ModuleRootModificationUtil.addDependency(mavenModule, antModule)
    checkModuleDependency(mavenModuleName, antModuleName)

    project.stateStore.save()
    var fileText = modulesFile.readText()
    assertEquals(2, listOf(antModuleName, mavenModuleName).filter { fileText.contains(it) }.size)

    assertTrue(antModuleFile.exists())
    assertTrue(mavenModuleFile.exists())
    assertTrue(mavenModuleFile.readText().contains(antModuleName))

    projectModel.renameModule(antModule, gradleModuleName)
    checkModuleDependency(mavenModuleName, gradleModuleName)

    project.stateStore.save()
    fileText = modulesFile.readText()
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
      startLog()
      val moduleManager = ModuleManager.getInstance(project)
      for (module in moduleManager.modules) {
        moduleManager.disposeModule(module)
      }

      val moduleDirUrl = virtualFileManager.fromPath(project.basePath!!)
      val projectModel = WorkspaceModel.getInstance(project)

      projectModel.updateProjectModel {
        it addEntity ModuleEntity("name", emptyList(),
                                  JpsProjectFileEntitySource.FileInDirectory(moduleDirUrl, getJpsProjectConfigLocation(project)!!))
      }

      assertNotNull(moduleManager.findModuleByName("name"))

      projectModel.updateProjectModel {
        val moduleEntity = it.entities(ModuleEntity::class.java).single()
        it.removeEntity(moduleEntity)
        it addEntity ModuleEntity("name", emptyList(),
                                  JpsProjectFileEntitySource.FileInDirectory(moduleDirUrl, getJpsProjectConfigLocation(project)!!))
      }

      assertEquals(1, moduleManager.modules.size)
      assertNotNull(moduleManager.findModuleByName("name"))
      val caughtLogs = catchLog()
      // Trying to assert that a particular warning isn't presented in logs
      assertFalse("name.iml does not exist" in caughtLogs)
    }

  @Test
  fun `test remove and add module with the same name 2`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleManager = ModuleManager.getInstance(project)
    for (module in moduleManager.modules) {
      moduleManager.disposeModule(module)
    }

    val moduleDirUrl = virtualFileManager.fromPath(project.basePath!!)
    val projectModel = WorkspaceModel.getInstance(project)

    project.messageBus.connect(disposableRule.disposable).subscribe(WorkspaceModelTopics.CHANGED,
                                                                    object : WorkspaceModelChangeListener {
                                                                      override fun beforeChanged(event: VersionedStorageChange) {
                                                                        val moduleBridge = event.storageAfter.resolve(
                                                                          ModuleId("name"))!!.findModule(event.storageAfter)
                                                                        assertNotNull(moduleBridge)
                                                                      }

                                                                      override fun changed(event: VersionedStorageChange) {
                                                                        val moduleBridge = event.storageAfter.resolve(
                                                                          ModuleId("name"))!!.findModule(event.storageAfter)
                                                                        assertNotNull(moduleBridge)
                                                                      }
                                                                    }
    )

    projectModel.updateProjectModel {
      it addEntity ModuleEntity("name", emptyList(),
                                JpsProjectFileEntitySource.FileInDirectory(moduleDirUrl, getJpsProjectConfigLocation(project)!!))
    }

    assertNotNull(moduleManager.findModuleByName("name"))
  }

  @Test
  fun `test LibraryEntity is removed after removing module library order entry`() {
    val module = projectModel.createModule()

    ModuleRootModificationUtil.addModuleLibrary(module, "xxx-lib", listOf(File(project.basePath, "x.jar").path), emptyList())

    val projectModel = WorkspaceModel.getInstance(project)

    assertEquals(
      "xxx-lib",
      projectModel.currentSnapshot.entities(LibraryEntity::class.java).toList().single().name)

    ModuleRootModificationUtil.updateModel(module) { model ->
      val orderEntry = model.orderEntries.filterIsInstance<LibraryOrderEntry>().single()
      model.removeOrderEntry(orderEntry)
    }

    assertEmpty(projectModel.currentSnapshot.entities(LibraryEntity::class.java).toList())
  }

  @Test
  fun `test LibraryEntity is removed after clearing module order entries`() {
    val module = projectModel.createModule()

    ModuleRootModificationUtil.addModuleLibrary(module, "xxx-lib", listOf(File(project.basePath, "x.jar").path), emptyList())

    val projectModel = WorkspaceModel.getInstance(project)

    assertEquals(
      "xxx-lib",
      projectModel.currentSnapshot.entities(LibraryEntity::class.java).toList().single().name)

    ModuleRootModificationUtil.updateModel(module) { model ->
      model.clear()
    }

    assertEmpty(projectModel.currentSnapshot.entities(LibraryEntity::class.java).toList())
  }

  @Test
  fun `add remove excluded folder`() {
    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, temporaryDirectoryRule.newVirtualDirectory("root"))
    val excludedRoot = temporaryDirectoryRule.newVirtualDirectory("root/excluded")
    PsiTestUtil.addExcludedRoot(module, excludedRoot)
    val workspaceModel = WorkspaceModel.getInstance(project)
    assertEquals(excludedRoot, workspaceModel.currentSnapshot.entities(ExcludeUrlEntity::class.java).single().url.virtualFile)
    ModuleRootModificationUtil.modifyModel(module) { model ->
      val contentEntry = model.contentEntries.single()
      contentEntry.removeExcludeFolder(contentEntry.excludeFolders.single())
      true
    }
    assertEmpty(workspaceModel.currentSnapshot.entities(ExcludeUrlEntity::class.java).toList())
  }

  @Test
  fun `test content roots provided`() =
    WriteCommandAction.runWriteCommandAction(project) {
      val moduleManager = ModuleManager.getInstance(project)

      val dir = File(project.basePath, "dir")
      val moduleDirUrl = virtualFileManager.fromPath(project.basePath!!)
      val projectModel = WorkspaceModel.getInstance(project)

      val projectLocation = getJpsProjectConfigLocation(project)!!
      val virtualFileUrl = dir.toVirtualFileUrl(virtualFileManager)
      projectModel.updateProjectModel {
        val moduleEntity = it addEntity ModuleEntity("name", emptyList(),
                                                     JpsProjectFileEntitySource.FileInDirectory(moduleDirUrl, projectLocation))
        val contentRootEntity = it addEntity ContentRootEntity(virtualFileUrl, emptyList<@NlsSafe String>(), moduleEntity.entitySource) {
          module = moduleEntity
        }
        it addEntity SourceRootEntity(virtualFileUrl, "",
                                      JpsProjectFileEntitySource.FileInDirectory(moduleDirUrl, projectLocation)) {
          contentRoot = contentRootEntity
        }
      }

      val module = moduleManager.findModuleByName("name")
      assertNotNull(module)

      assertArrayEquals(
        arrayOf(virtualFileUrl.url),
        ModuleRootManager.getInstance(module!!).contentRootUrls
      )

      val sourceRootUrl = ModuleRootManager.getInstance(module).contentEntries.single()
        .sourceFolders.single().url
      assertEquals(virtualFileUrl.url, sourceRootUrl)
    }

  @Test
  fun `test module component serialized into module iml`() = runBlocking {
    val moduleFile = File(project.basePath, "test.iml")

    val moduleManager = ModuleManager.getInstance(project)
    val module = runWriteActionAndWait { moduleManager.newModule(moduleFile.path, ModuleTypeId.JAVA_MODULE) }

    project.stateStore.save()

    assertNull(JDomSerializationUtil.findComponent(JDOMUtil.load(moduleFile), "XXX"))

    TestModuleComponent.getInstance(module).testString = "My String"

    project.stateStore.save(forceSavingAllSettings = true)

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

    val module = projectModel.createModule()

    val modifiableModel = ApplicationManager.getApplication().runReadAction<ModifiableRootModel> {
      ModuleRootManager.getInstance(module).modifiableModel
    }
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
  fun `test module extension saves correctly if custom options exist`() {
    val optionValue = "foo"
    val module = projectModel.createModule()
    module.setOption(Module.ELEMENT_TYPE, optionValue)

    val moduleRootManager = ModuleRootManager.getInstance(module)
    var modifiableModel = moduleRootManager.modifiableModel
    var moduleExtension = modifiableModel.getModuleExtension(TestModuleExtension::class.java)
    moduleExtension.languageLevel = LanguageLevel.JDK_1_5
    runWriteActionAndWait { modifiableModel.commit() }

    assertEquals(LanguageLevel.JDK_1_5, moduleRootManager.getModuleExtension(TestModuleExtension::class.java).languageLevel)

    modifiableModel = moduleRootManager.modifiableModel
    moduleExtension = modifiableModel.getModuleExtension(TestModuleExtension::class.java)
    moduleExtension.languageLevel = null
    runWriteActionAndWait { modifiableModel.commit() }

    assertEquals(optionValue, module.getOptionValue(Module.ELEMENT_TYPE))
    assertNull(moduleRootManager.getModuleExtension(TestModuleExtension::class.java).languageLevel)
  }

  @Test
  fun `test module libraries loaded from cache`() {
    val builder = MutableEntityStorage.create()

    val tempDir = temporaryDirectoryRule.newDirectoryPath()

    val iprFile = tempDir.resolve("testProject.ipr")
    val configLocation = toConfigLocation(iprFile, virtualFileManager)
    val source = JpsProjectFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)
    val moduleEntity = builder addEntity ModuleEntity(name = "test", dependencies = emptyList(), entitySource = source)
    val moduleLibraryEntity = builder addEntity LibraryEntity(name = "some",
                                                              tableId = LibraryTableId.ModuleLibraryTableId(moduleEntity.symbolicId),
                                                              roots = listOf(LibraryRoot(tempDir.toVirtualFileUrl(virtualFileManager),
                                                                                         LibraryRootTypeId.COMPILED)),
                                                              entitySource = source)
    builder.modifyEntity(moduleEntity) {
      dependencies = listOf(
        ModuleDependencyItem.Exportable.LibraryDependency(moduleLibraryEntity.symbolicId, false,
                                                          ModuleDependencyItem.DependencyScope.COMPILE)
      ).toMutableList()
    }

    WorkspaceModelInitialTestContent.withInitialContent(builder.toSnapshot()) {
      val project = PlatformTestUtil.loadAndOpenProject(iprFile, disposableRule.disposable)

      val module = ModuleManager.getInstance(project).findModuleByName("test")

      val rootManager = ModuleRootManager.getInstance(module!!)
      val libraries = OrderEntryUtil.getModuleLibraries(rootManager)
      assertEquals(1, libraries.size)

      val libraryOrderEntry = rootManager.orderEntries[0] as LibraryOrderEntry
      assertTrue(libraryOrderEntry.isModuleLevel)
      assertSame(libraryOrderEntry.library, libraries[0])
      assertEquals(JpsLibraryTableSerializer.MODULE_LEVEL, libraryOrderEntry.libraryLevel)
      assertSameElements(libraryOrderEntry.getRootUrls(OrderRootType.CLASSES), tempDir.toVirtualFileUrl(virtualFileManager).url)
    }
  }

  @Test
  fun `test libraries are loaded from cache`() {
    val builder = MutableEntityStorage.create()

    val tempDir = temporaryDirectoryRule.newDirectoryPath()

    val iprFile = tempDir.resolve("testProject.ipr")
    val jarUrl = tempDir.resolve("a.jar").toVirtualFileUrl(virtualFileManager)
    val source = JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(toConfigLocation(iprFile, virtualFileManager))
    builder addEntity LibraryEntity(name = "my_lib",
                                    tableId = LibraryTableId.ProjectLibraryTableId,
                                    roots = listOf(LibraryRoot(jarUrl, LibraryRootTypeId.COMPILED)),
                                    entitySource = source)

    WorkspaceModelInitialTestContent.withInitialContent(builder.toSnapshot()) {
      val project = PlatformTestUtil.loadAndOpenProject(iprFile, disposableRule.disposable)

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
    TestCustomRootModelSerializerExtension.registerTestCustomSourceRootType(temporaryDirectoryRule.newDirectoryPath().toFile(),
                                                                            disposableRule.disposable)
    val tempDir = temporaryDirectoryRule.newDirectoryPath()
    val moduleImlFile = tempDir.resolve("my.iml")
    Files.createDirectories(moduleImlFile.parent)
    val url = VfsUtilCore.pathToUrl(moduleImlFile.parent.toString())
    moduleImlFile.write("""
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
      val module = moduleManager.loadModule(moduleImlFile)
      val contentEntry = ModuleRootManager.getInstance(module).contentEntries.single()

      assertEquals(2, contentEntry.sourceFolders.size)

      assertTrue(contentEntry.sourceFolders[0].rootType is TestCustomSourceRootType)
      assertEquals("x y z", (contentEntry.sourceFolders[0].jpsElement.properties as TestCustomSourceRootProperties).testString)

      assertTrue(contentEntry.sourceFolders[1].rootType is TestCustomSourceRootType)
      assertEquals("default properties", (contentEntry.sourceFolders[1].jpsElement.properties as TestCustomSourceRootProperties).testString)

      val customRoots = WorkspaceModel.getInstance(project).currentSnapshot.entities(CustomSourceRootPropertiesEntity::class.java)
        .toList()
        .sortedBy { it.sourceRoot.url.url }
      assertEquals(1, customRoots.size)

      assertEquals("<sourceFolder testString=\"x y z\" />", customRoots[0].propertiesXmlTag)
      assertEquals("$url/root1", customRoots[0].sourceRoot.url.url)
      assertEquals(TestCustomSourceRootType.TYPE_ID, customRoots[0].sourceRoot.rootType)
    }
  }

  @Test
  fun `test unknown custom source root type`() {
    val tempDir = temporaryDirectoryRule.newDirectoryPath()
    val moduleImlFile = tempDir.resolve("my.iml")
    Files.createDirectories(moduleImlFile.parent)
    moduleImlFile.write("""
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
      val module = moduleManager.loadModule(moduleImlFile)
      val contentEntry = ModuleRootManager.getInstance(module).contentEntries.single()
      val sourceFolder = contentEntry.sourceFolders.single()

      assertSame(UnknownSourceRootType.getInstance("unsupported-custom-source-root-type"), sourceFolder.rootType)
      assertTrue(sourceFolder.jpsElement.properties is UnknownSourceRootTypeProperties<*>)

      val customRoot = WorkspaceModel.getInstance(project).currentSnapshot.entities(CustomSourceRootPropertiesEntity::class.java)
        .toList().single()

      assertEquals("<sourceFolder param1=\"x y z\" />", customRoot.propertiesXmlTag)
      assertEquals("unsupported-custom-source-root-type", customRoot.sourceRoot.rootType)
    }
  }

  @Test
  fun `test custom source root saving`() = runBlocking {
    val tempDir = temporaryDirectoryRule.newDirectoryPath().toFile()
    TestCustomRootModelSerializerExtension.registerTestCustomSourceRootType(temporaryDirectoryRule.newDirectoryPath().toFile(),
                                                                            disposableRule.disposable)

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
    }

    project.stateStore.save()

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

  @Test
  fun `test remove module removes source roots`() = runBlocking {
    startLog()
    val moduleName = "build"
    val antLibraryFolder = "ant-lib"

    val moduleFile = File(project.basePath, "$moduleName.iml")

    val module = withContext(Dispatchers.EDT) {
      runWriteAction {
        val module = ModuleManager.getInstance(project).getModifiableModel().let { moduleModel ->
          val module = moduleModel.newModule(moduleFile.path, EmptyModuleType.getInstance().id) as ModuleBridge
          moduleModel.commit()
          module
        }

        ModuleRootModificationUtil.updateModel(module) { model ->
          val tempDir = temporaryDirectoryRule.newDirectoryPath().toFile()
          val url = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(tempDir.path))
          val contentEntry = model.addContentEntry(url)
          contentEntry.addSourceFolder("$url/$antLibraryFolder", false)
        }

        module
      }
    }

    project.stateStore.save()

    withContext(Dispatchers.EDT) {
      assertTrue(moduleFile.readText().contains(antLibraryFolder))
      val entityStore = (WorkspaceModel.getInstance(project) as WorkspaceModelImpl).entityStorage
      assertEquals(1, entityStore.current.entities(ContentRootEntity::class.java).count())
      assertEquals(1, entityStore.current.entities(JavaSourceRootPropertiesEntity::class.java).count())

      ApplicationManager.getApplication().runWriteAction {
        ModuleManager.getInstance(project).disposeModule(module)
      }
      assertEmpty(entityStore.current.entities(ContentRootEntity::class.java).toList())
      assertEmpty(entityStore.current.entities(JavaSourceRootPropertiesEntity::class.java).toList())
    }

    val caughtLogs = catchLog()
    // Trying to assert that a particular warning isn't presented in logs
    assertFalse("name.iml does not exist" in caughtLogs)
  }

  @Test
  fun `test remove content entity removes related source folders`() = WriteCommandAction.runWriteCommandAction(project) {
    val antLibraryFolder = "ant-lib"

    val module = projectModel.createModule()
    ModuleRootModificationUtil.updateModel(module) { model ->
      val tempDir = temporaryDirectoryRule.newDirectoryPath().toFile()
      val url = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(tempDir.path))
      val contentEntry = model.addContentEntry(url)
      contentEntry.addSourceFolder("$url/$antLibraryFolder", false)
    }

    val entityStore = (WorkspaceModel.getInstance(project) as WorkspaceModelImpl).entityStorage
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
    val module = projectModel.createModule()

    project.messageBus.connect(disposableRule.disposable).subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        val modules = ModuleManager.getInstance(event.project).modules
        assertEmpty(modules)
      }
    })

    ModuleManager.getInstance(project).disposeModule(module)
  }

  @Test
  fun `creating module from modifiable model and removing it`() =
    WriteCommandAction.runWriteCommandAction(project) {
      projectModel.createModule("xxx")
      val moduleManager = ModuleManager.getInstance(project) as ModuleManagerBridgeImpl

      moduleManager.getModifiableModel(WorkspaceModel.getInstance(project).currentSnapshot.toBuilder()).let { modifiableModel ->
        val existingModule = modifiableModel.modules.single { it.name == "xxx" }
        modifiableModel.disposeModule(existingModule)
        val module = projectModel.createModule("xxx", modifiableModel)
        // No commit

        // This is an actual code that was broken
        //   It tests that model listener inside of ModuleBridgeImpl correctly converts an instanse of the storage inside of it
        WorkspaceModel.getInstance(project).updateProjectModel {
          val moduleEntity: ModuleEntity = it.entities(ModuleEntity::class.java).single()
          it.removeEntity(moduleEntity)
        }

        assertEquals("xxx", module.name)

        modifiableModel.commit()
      }
    }

  @Test
  fun `remove module without removing module library`() = WriteCommandAction.runWriteCommandAction(project) {
    val module = projectModel.createModule()

    val componentBridge = ModuleRootComponentBridge.getInstance(module)
    val componentModifiableModel = componentBridge.modifiableModel
    val modifiableModel = componentModifiableModel.moduleLibraryTable.modifiableModel
    modifiableModel.createLibrary("myNewLibrary")
    modifiableModel.commit()
    componentModifiableModel.commit()

    val currentStore = WorkspaceModel.getInstance(project).currentSnapshot
    UsefulTestCase.assertOneElement(currentStore.entities(ModuleEntity::class.java).toList())
    UsefulTestCase.assertOneElement(currentStore.entities(LibraryEntity::class.java).toList())

    WorkspaceModel.getInstance(project).updateProjectModel {
      val moduleEntity = it.entities(ModuleEntity::class.java).single()
      it.removeEntity(moduleEntity)
    }

    val updatedStore = WorkspaceModel.getInstance(project).currentSnapshot
    assertEmpty(updatedStore.entities(ModuleEntity::class.java).toList())
    assertEmpty(updatedStore.entities(LibraryEntity::class.java).toList())
  }

  @Test
  fun `recreate module`() = WriteCommandAction.runWriteCommandAction(project) {
    val moduleManager = ModuleManager.getInstance(project)

    val module = projectModel.createModule("xxx")

    val newModule = moduleManager.getModifiableModel().let { modifiableModel ->
      modifiableModel.disposeModule(module)
      val newModule = projectModel.createModule("xxx", modifiableModel)
      modifiableModel.commit()
      newModule
    }

    WorkspaceModel.getInstance(project).updateProjectModel { builder ->
      val entity = builder.resolve(ModuleId("xxx"))!!
      builder.modifyEntity(entity) {
        this.name = "yyy"
      }
    }
    assertEquals("yyy", newModule.name)
  }

  class OutCatcher(printStream: PrintStream) : PrintStream(printStream) {
    var catcher = ""
    override fun println(x: String?) {
      catcher += "$x\n"
    }
  }

  companion object {
    @ClassRule
    @JvmField
    val application = ApplicationRule()

    val log = logger<ModuleBridgesTest>()
    var logLine = ""
    val rand = Random(0)

    fun startLog() {
      logLine = "Start logging " + rand.nextInt()
      log.info(logLine)
    }

    fun catchLog(): String {
      val outCatcher = OutCatcher(System.out)
      TestLoggerFactory.dumpLogTo(logLine, outCatcher)
      return outCatcher.catcher
    }
  }
}

internal fun createEmptyTestProject(temporaryDirectory: TemporaryDirectory, disposableRule: DisposableRule): Project {
  val projectDir = temporaryDirectory.newPath("project")
  val project = WorkspaceModelInitialTestContent.withInitialContent(ImmutableEntityStorage.empty()) {
    PlatformTestUtil.loadAndOpenProject(projectDir.resolve("testProject.ipr"), disposableRule.disposable)
  }
  return project
}
