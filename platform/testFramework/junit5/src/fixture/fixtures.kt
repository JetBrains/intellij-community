// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.testFramework.junit5.fixture

import com.intellij.execution.RunManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.intellij.platform.eel.fs.EelFileSystemApi.CreateTemporaryEntryOptions
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.util.coroutines.childScope
import com.intellij.project.stateStore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.common.EditorCaretTestUtil
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.replaceService
import com.intellij.ui.docking.DockManager
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

@JvmOverloads
@TestOnly
fun testNameFixture(lowerCaseFirstLetter: Boolean = true): TestFixture<String> = testFixture {
  val testName = if (lowerCaseFirstLetter) {
    it.testName.replaceFirstChar { chr -> chr.lowercaseChar() }
  }
  else {
    it.testName
  }

  initialized(testName) {}
}

@JvmOverloads
@TestOnly
fun tempPathFixture(root: Path? = null, prefix: String = "IJ", subdirName: String? = null): TestFixture<Path> = testFixture {
  var tempDir = withContext(Dispatchers.IO) {
    if (root == null) {
      it.eel?.fs?.createTemporaryDirectory(CreateTemporaryEntryOptions.Builder().prefix(prefix).build())?.getOrThrow()?.asNioPath()
      ?: Files.createTempDirectory(prefix)
    }
    else {
      if (!root.exists()) {
        root.createDirectories()
      }
      Files.createTempDirectory(root, prefix)
    }
  }
  if (subdirName != null) {
    tempDir = tempDir.resolve(subdirName).createDirectory()
  }
  val realTempDir = tempDir.toRealPath()
  initialized(realTempDir) {
    withContext(Dispatchers.IO) {
      repeat(10) {
        try {
          // This method might throw DirectoryNotEmptyException due to races, hence retry
          realTempDir.delete(recursively = true)
          return@withContext
        }
        catch (e: IOException) {
          fileLogger().warn("Can't delete $realTempDir", e)
          Thread.sleep(100)
        }
      }
      realTempDir.delete(recursively = true)
    }
  }
}

@TestOnly
fun TestFixture<Project>.pathInProjectFixture(path: Path): TestFixture<Path> {
  return testFixture {
    val project = init()
    val subpath = project.stateStore.projectBasePath.resolve(path)
    initialized(subpath) {
      // will be removed with project directory
    }
  }
}

@TestOnly
fun TestFixture<Project>.fileOrDirInProjectFixture(relativePath: String): TestFixture<VirtualFile> = testFixture {
  val filePath = pathInProjectFixture(Path(relativePath)).init()
  val file = filePath.refreshAndFindVirtualFileOrDirectory()
             ?: throw IllegalStateException("File not found: $relativePath, absolutePath: $filePath")

  initialized(file) {}
}

@TestOnly
fun TestFixture<Project>.moduleInProjectFixture(name: String): TestFixture<Module> = testFixture {
  val project = init()
  val module = ModuleManager.getInstance(project).findModuleByName(name) ?: throw IllegalStateException("Module not found: $name")
  initialized(module) {}
}

/**
 * Creates [Project] fixture. If the fixture is stored in a static variable, the [Project] will be created
 * only once. On the contrary, storing a fixture in the instance variable will create a new [Project] for each test.
 *
 * <p>
 *
 * NOTE: the behavior of disposal is different from JUnit3, e.g., it is not possible to share [Project] instance when running
 * different test classes.
 * See the showcase for usage examples.
 * @see com.intellij.testFramework.junit5.showcase.JUnit5ProjectFixtureTest
 */
@JvmOverloads
@TestOnly
fun projectFixture(
  pathFixture: TestFixture<Path> = tempPathFixture(),
  openProjectTask: OpenProjectTask = OpenProjectTask.build(),
  openAfterCreation: Boolean = false,
): TestFixture<Project> = testFixture {
  // Background service preloading might trigger service loading after a project gets disposed leading to a test failure.
  val openProjectTask = openProjectTask.copy(preloadServices = false)
  val path = pathFixture.init()
  val project = ProjectManagerEx.getInstanceEx().newProjectAsync(path, openProjectTask)
  if (openAfterCreation) {
    ProjectManagerEx.getInstanceEx().openProject(path, openProjectTask.withProject(project))
  }
  // Wait until components fully loaded. Otherwise, we might start loading then when a project is already disposed when a test is too fast.
  project.serviceAsync<RunManager>()
  initialized(project) {
    ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project, save = false)
  }
}

@JvmOverloads
@TestOnly
fun TestFixture<Project>.moduleFixture(
  name: String? = null,
  moduleType: String? = null,
): TestFixture<Module> = testFixture(name ?: "unnamed module") { context ->
  val project = this@moduleFixture.init()
  val manager = ModuleManager.getInstance(project)
  val module = edtWriteAction {
    manager.newNonPersistentModule(name ?: context.uniqueId, "")
  }
  moduleType?.let { module.setModuleType(it) }
  initialized(module) {
    edtWriteAction {
      if (!module.isDisposed) {
        manager.disposeModule(module)
      }
    }
  }
}

/**
 * Creates module on [pathFixture].
 * If [addPathToSourceRoot], we add [pathFixture] to the module sources,
 * which is convenient for the scripting languages where module root is also source root.
 * See the showcase for usage examples.
 * @see com.intellij.testFramework.junit5.showcase.JUnit5ModuleFixtureTest
 */
@JvmOverloads
@TestOnly
fun TestFixture<Project>.moduleFixture(
  pathFixture: TestFixture<Path>,
  addPathToSourceRoot: Boolean = false,
  moduleTypeId: String = ""
): TestFixture<Module> = testFixture { _ ->
  val project = this@moduleFixture.init()
  val path = pathFixture.init()
  val manager = ModuleManager.getInstance(project)
  val module = edtWriteAction {
    manager.newModule(path, moduleTypeId)
  }
  if (addPathToSourceRoot) {
    val pathVfs = withContext(Dispatchers.IO) {
      requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)) {
        "Path provided by pathFixture should exist: $path"
      }
    }

    edtWriteAction {
      ModuleRootManager.getInstance(module).modifiableModel.apply {
        addContentEntry(pathVfs).addSourceFolder(pathVfs, false)
        commit()
      }
    }
  }
  initialized(module) {
    edtWriteAction {
      manager.disposeModule(module)
    }
  }
}

/**
 * Creates [Disposable] fixture. See the showcase for usage examples.
 * @see com.intellij.testFramework.junit5.showcase.JUnit5DisposableFixture
 * @see com.intellij.testFramework.junit5.showcase.JUnit5DisposableFixtureTest
 */
@TestOnly
fun disposableFixture(): TestFixture<Disposable> = testFixture { context ->
  val disposable = Disposer.newCheckedDisposable(context.uniqueId)
  initialized(disposable) {
    Disposer.dispose(disposable)
  }
}


/**
 * The fixture represents a directory within a module's content root, marked as either a source or a test source directory.
 * Optionally, it can copy the content of a specified resource path into the created directory.
 *
 * @param [isTestSource] Specifies whether the directory should be marked as a test source (true)
 *        or a source directory (false).
 * @param [blueprintResourcePath] An optional path to a resource whose contents will be copied to the
 *        created directory.
 * @return A fixture providing a PsiDirectory instance representing the initialized source root.
 */
@JvmOverloads
@OptIn(ExperimentalPathApi::class)
@TestOnly
fun TestFixture<Module>.sourceRootFixture(
  isTestSource: Boolean = false,
  pathFixture: TestFixture<Path> = tempPathFixture(),
  blueprintResourcePath: Path? = null,
): TestFixture<PsiDirectory> =
  testFixture { _ ->
    val module = this@sourceRootFixture.init()
    val directoryPath: Path = pathFixture.init()
    val directoryVfs = VfsUtil.createDirectories(directoryPath.toCanonicalPath())

    blueprintResourcePath?.let {
      require(it.exists()) { "Blueprint resource path provided does not exist: $it" }
      it.copyToRecursively(directoryPath, followLinks = false, overwrite = true)
    }

    ModuleRootModificationUtil.updateModel(module) { model ->
      model.addContentEntry(directoryVfs).addSourceFolder(directoryVfs, isTestSource)
    }
    val directory = readAction {
      PsiManager.getInstance(module.project).findDirectory(directoryVfs) ?: error("Fail to find directory $directoryVfs")
    }
    initialized(directory) {
      edtWriteAction {
        directory.delete()
      }
    }
  }

/**
 * Creates [PsiFile] fixture. See the showcase for usage examples.
 * @see com.intellij.testFramework.junit5.showcase.JUnit5PsiFileFixtureTest
 */
@TestOnly
fun TestFixture<PsiDirectory>.psiFileFixture(
  name: String,
  content: String,
): TestFixture<PsiFile> = testFixture { _ ->
  val project = this@psiFileFixture.init().project
  val virtualFile = virtualFileFixture(name, content).init()
  val file = PsiDocumentManager.getInstance(project).commitAndRunReadAction(Computable {
    PsiManager.getInstance(project).findFile(virtualFile) ?: error("Fail to find file $virtualFile")
  })
  initialized(file) {/*nothing*/ }
}

@TestOnly
fun TestFixture<PsiDirectory>.virtualFileFixture(
  name: String,
  content: String,
): TestFixture<VirtualFile> = testFixture { _ ->
  val dirFixture = this@virtualFileFixture
  val dir = dirFixture.init()
  val file = edtWriteAction {
    dir.virtualFile.createChildData(dirFixture, name).also {
      it.setBinaryContent(content.toByteArray())
    }
  }
  initialized(file) {
    edtWriteAction {
      file.delete(dirFixture)
    }
  }
}

/**
 * Creates [Editor] fixture. See the showcase for usage examples.
 * @see com.intellij.testFramework.junit5.showcase.JUnit5EditorFixtureTest
 */
@TestOnly
fun TestFixture<PsiFile>.editorFixture(): TestFixture<Editor> = testFixture { _ ->
  val psiFile = this@editorFixture.init()
  val project = psiFile.project
  val file = psiFile.virtualFile
  val editor = withContext(Dispatchers.UiWithModelAccess) {
    val fileEditorManager = project.serviceAsync<FileEditorManager>()
    writeAction {
      val editor = fileEditorManager.openTextEditor(OpenFileDescriptor(project, file), true)
      requireNotNull(editor)

      val caretAndSelection = EditorCaretTestUtil.extractCaretAndSelectionMarkers(editor.document)
      if (caretAndSelection.hasExplicitCaret()) {
        EditorCaretTestUtil.setCaretsAndSelection(editor, caretAndSelection)
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
      }
      editor
    }
  }
  initialized(editor) {
    withContext(Dispatchers.UiWithModelAccess) {
      val fileEditorManager = project.serviceAsync<FileEditorManager>()
      writeAction {
        fileEditorManager.closeFile(file)
      }
    }
    val editorHistoryManager = project.serviceAsync<EditorHistoryManager>()
    readAction {
      val virtualFile = PsiManager.getInstance(project).findFile(file)?.virtualFile
      if (virtualFile != null) {
        editorHistoryManager.removeFile(file)
      }
    }
  }
}

/**
 * Creates [FileEditorManagerImpl] fixture for [project][TestFixture].
 *
 * This is a JUnit 5 fixture alternative to `FileEditorManagerTestCase`.
 */
@TestOnly
fun TestFixture<Project>.fileEditorManagerFixture(initDockableContentFactory: Boolean = false): TestFixture<FileEditorManagerImpl> = testFixture {
  val project = this@fileEditorManagerFixture.init()
  project.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true)

  val manager = FileEditorManagerImpl(project, (project as ComponentManagerEx).getCoroutineScope().childScope("FileEditorManagerFixture"))
  if (initDockableContentFactory) {
    manager.initDockableContentFactory()
  }

  val disposable = Disposer.newDisposable()
  project.replaceService(FileEditorManager::class.java, manager, disposable)
  val providerManager = FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl
  runBlocking {
    withContext(Dispatchers.UiWithModelAccess) {
      providerManager.clearSelectedProviders()
      val dockContainerCount = DockManager.getInstance(project).containers.size
      check(dockContainerCount == 1) {
        "The previous test didn't clear the state (containers: $dockContainerCount)"
      }
    }
  }

  initialized(manager) {
    runAll(
      {
        runBlocking {
          withContext(Dispatchers.UiWithModelAccess) {
            writeAction {
              manager.closeAllFiles()
            }
          }
        }
      },
      {
        runBlocking {
          withContext(Dispatchers.UiWithModelAccess) {
            project.serviceIfCreated<EditorHistoryManager>()?.removeAllFiles()
          }
        }
      },
      {
        runBlocking {
          withContext(Dispatchers.UiWithModelAccess) {
            providerManager.clearSelectedProviders()
          }
        }
      },
      { Disposer.dispose(disposable) },
      {
        runBlocking {
          withContext(Dispatchers.UiWithModelAccess) {
            val dockContainers = project.serviceIfCreated<DockManager>()?.containers.orEmpty()
            val dockContainerCount = dockContainers.size
            check(dockContainerCount <= 1) {
              "The previous test didn't clear the state (containers: $dockContainerCount)"
            }
          }
        }
      },
    )
  }
}

@TestOnly
fun <T : Any> extensionPointFixture(epName: ExtensionPointName<in T>, createExtension: suspend () -> T): TestFixture<T> = testFixture {
  val extension = createExtension()
  val disposable = Disposer.newDisposable()
  epName.point.registerExtension(extension, disposable)
  initialized(extension) {
    Disposer.dispose(disposable)
  }
}

@TestOnly
fun registryKeyFixture(@NonNls key: String, setValue: RegistryValue.() -> Unit): TestFixture<RegistryValue> = testFixture {
  val registryValue = Registry.get(key)
  val previousValue = registryValue.asString()
  setValue(registryValue)

  initialized(registryValue) {
    registryValue.setValue(previousValue)
  }
}

@TestOnly
fun <T : Any> Application.replacedServiceFixture(
  serviceInterface: Class<in T>,
  createService: suspend () -> T,
): TestFixture<T> = replacedServiceFixtureInner(
  getComponentManager = { this@replacedServiceFixture },
  serviceInterface,
  createService
)

@TestOnly
fun <T : Any> TestFixture<Project>.replacedServiceFixture(
  serviceInterface: Class<in T>,
  createService: suspend () -> T,
): TestFixture<T> = replacedServiceFixtureInner(
  getComponentManager = { this@replacedServiceFixture.init() },
  serviceInterface,
  createService
)

@TestOnly
private fun <T : Any> replacedServiceFixtureInner(
  getComponentManager: suspend TestFixtureInitializer.R<T>.() -> ComponentManager,
  serviceInterface: Class<in T>,
  createService: suspend () -> T,
) = testFixture {
  val service = createService()
  val disposable = Disposer.newDisposable()
  getComponentManager().replaceService(serviceInterface, service, disposable)
  initialized(service) {
    Disposer.dispose(disposable)
  }
}
