// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.rules

import com.intellij.facet.Facet
import com.intellij.facet.FacetConfiguration
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.facet.impl.FacetUtil
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.junit.jupiter.api.extension.*
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

open class ProjectModelRule : TestRule {
  val baseProjectDir: TempDirectory = TempDirectory()
  val disposableRule: DisposableRule = DisposableRule()

  lateinit var project: Project
  lateinit var projectRootDir: Path
  lateinit var filePointerTracker: VirtualFilePointerTracker

  private val projectResource = ProjectResource()

  inner class ProjectResource : ExternalResource() {
    public override fun before() {
      projectRootDir = baseProjectDir.root.toPath()
      project = PlatformTestUtil.loadAndOpenProject(projectRootDir, disposableRule.disposable)
      filePointerTracker = VirtualFilePointerTracker()
    }

    public override fun after() {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      filePointerTracker.assertPointersAreDisposed()
    }
  }

  private val ruleChain = RuleChain(baseProjectDir, projectResource, disposableRule)

  override fun apply(base: Statement, description: Description): Statement {
    return ruleChain.apply(base, description)
  }

  @JvmOverloads
  fun createModule(name: String = "module", moduleBaseDir: Path = projectRootDir): Module {
    val imlFile = generateImlPath(name, moduleBaseDir)
    val manager = moduleManager
    return runWriteActionAndWait {
      manager.newModule(imlFile, EmptyModuleType.EMPTY_MODULE)
    }.also {
      IndexingTestUtil.waitUntilIndexesAreReady(project)
    }
  }

  fun createModule(name: String, moduleModel: ModifiableModuleModel): Module {
    return moduleModel.newModule(generateImlPath(name), EmptyModuleType.EMPTY_MODULE).also {
      IndexingTestUtil.waitUntilIndexesAreReady(project)
    }
  }

  fun addSourceRoot(module: Module, relativePath: String, rootType: JpsModuleSourceRootType<*>): VirtualFile {
    val srcRoot = baseProjectDir.newVirtualDirectory("${module.name}/$relativePath")
    ModuleRootModificationUtil.updateModel(module) { model ->
      val contentRootUrl = VfsUtil.pathToUrl(projectRootDir.resolve(module.name).invariantSeparatorsPathString)
      val contentEntry = model.contentEntries.find { it.url == contentRootUrl } ?: model.addContentEntry(contentRootUrl)
      require(contentEntry.sourceFolders.none { it.url == srcRoot.url }) { "Source folder $srcRoot already exists" }
      contentEntry.addSourceFolder(srcRoot, rootType)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    return srcRoot
  }

  private fun generateImlPath(name: String, rootDir: Path = projectRootDir): Path {
    return rootDir.resolve("$name/$name.iml")
  }

  fun createSdk(name: String = "sdk", setup: (SdkModificator) -> Unit = {}): Sdk {
    val sdk = ProjectJdkTable.getInstance().createSdk(name, sdkType)
    modifySdk(sdk, setup)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    return sdk
  }

  fun modifySdk(sdk: Sdk, setup: (SdkModificator) -> Unit) {
    val sdkModificator = sdk.sdkModificator
    try {
      setup(sdkModificator)
    }
    finally {
      val application = ApplicationManager.getApplication()
      val runnable = { sdkModificator.commitChanges() }
      if (application.isDispatchThread) {
        runWriteAction(runnable)
      } else {
        application.invokeAndWait { runWriteAction(runnable) }
      }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  fun addSdk(name: String = "sdk", setup: (SdkModificator) -> Unit = {}): Sdk {
    val sdk = createSdk(name, setup)
    addSdk(sdk)
    return sdk
  }
  
  fun addSdk(sdk: Sdk) {
    runWriteActionAndWait {
      ProjectJdkTable.getInstance().addJdk(sdk, disposableRule.disposable)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  fun addProjectLevelLibrary(name: String, setup: (LibraryEx.ModifiableModelEx) -> Unit = {}): LibraryEx {
    return addLibrary(name, projectLibraryTable, setup)
  }

  fun addModuleLevelLibrary(module: Module, name: String, setup: (LibraryEx.ModifiableModelEx) -> Unit = {}): LibraryEx {
    val library = Ref.create<LibraryEx>()
    ModuleRootModificationUtil.updateModel(module) { model ->
      library.set(addLibrary(name, model.moduleLibraryTable, setup))
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    return library.get()
  }

  fun addLibrary(name: String, libraryTable: LibraryTable, setup: (LibraryEx.ModifiableModelEx) -> Unit = {}): LibraryEx {
    val model = libraryTable.modifiableModel
    val library = model.createLibrary(name) as LibraryEx
    val libraryModel = library.modifiableModel
    setup(libraryModel)
    runWriteActionAndWait {
      libraryModel.commit()
      model.commit()
    }
    if (libraryTable.tableLevel !in setOf(LibraryTableImplUtil.MODULE_LEVEL, LibraryTablesRegistrar.PROJECT_LEVEL)) {
      disposeOnTearDown(library)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    return library
  }

  fun addApplicationLevelLibrary(name: String, setup: (LibraryEx.ModifiableModelEx) -> Unit = {}): LibraryEx {
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    return addLibrary(name, libraryTable, setup)
  }

  private fun disposeOnTearDown(library: LibraryEx) {
    disposableRule.register {
      runWriteActionAndWait {
        if (!library.isDisposed && library.table.getLibraryByName(library.name!!) == library) {
          library.table.removeLibrary(library)
        }
      }
    }
  }

  fun createLibraryAndDisposeOnTearDown(name: String, model: LibraryTable.ModifiableModel): LibraryEx {
    val library = model.createLibrary(name) as LibraryEx
    disposeOnTearDown(library)
    return library
  }

  fun renameLibrary(library: Library, newName: String) {
    modifyLibrary(library) {
      it.name = newName
    }
  }
  
  fun modifyLibrary(library: Library, action: (LibraryEx.ModifiableModelEx) -> Unit) {
    val model = library.modifiableModel as LibraryEx.ModifiableModelEx
    action(model)
    runWriteActionAndWait { model.commit() }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  fun renameModule(module: Module, newName: String) {
    val model = runReadAction { moduleManager.getModifiableModel() }
    model.renameModule(module, newName)
    runWriteActionAndWait { model.commit() }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  fun removeModule(module: Module) {
    runWriteActionAndWait { moduleManager.disposeModule(module) }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  fun setUnloadedModules(vararg moduleName: String) {
    runUnderModalProgressIfIsEdt {
      moduleManager.setUnloadedModules(moduleName.toList())
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  fun <F: Facet<C>, C: FacetConfiguration> addFacet(module: Module, type: FacetType<F, C>, configuration: C = type.createDefaultConfiguration()): F {
    val facetManager = FacetManager.getInstance(module)
    val model = facetManager.createModifiableModel()
    val facet = facetManager.createFacet(type, type.defaultFacetName, configuration, null)
    model.addFacet(facet)
    runWriteActionAndWait { model.commit() }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    return facet
  }

  fun removeFacet(facet: Facet<*>) {
    FacetUtil.deleteFacet(facet)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  protected fun setUp(methodName: String) {
    baseProjectDir.before(methodName)
    projectResource.before()
  }

  protected fun tearDown() {
    disposableRule.after()
    projectResource.after()
    baseProjectDir.after()
  }

  val sdkType: SdkTypeId
    get() = SimpleJavaSdkType.getInstance()

  val projectRootManager: ProjectRootManager
    get() = ProjectRootManager.getInstance(project)

  val moduleManager: ModuleManager
    get() = ModuleManager.getInstance(project)

  val projectLibraryTable: LibraryTable
    get() = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
}

class ProjectModelExtension : ProjectModelRule(), BeforeEachCallback, AfterEachCallback {
  override fun beforeEach(context: ExtensionContext) {
    setUp(context.displayName)
  }

  override fun afterEach(context: ExtensionContext) {
    tearDown()
  }
}

class ClassLevelProjectModelExtension : ProjectModelRule(), BeforeAllCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    setUp(context.displayName)
  }

  override fun afterAll(context: ExtensionContext) {
    tearDown()
  }
}