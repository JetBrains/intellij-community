// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.execution.RunManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.fs.EelFileSystemApi.CreateTemporaryEntryOptions
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.exists

@TestOnly
fun tempPathFixture(root: Path? = null, prefix: String = "IJ"): TestFixture<Path> = testFixture {
  val tempDir = withContext(Dispatchers.IO) {
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
  val realTempDir = tempDir.toRealPath()
  initialized(realTempDir) {
    withContext(Dispatchers.IO) {
      realTempDir.delete(recursively = true)
    }
  }
}

@TestOnly
fun TestFixture<Project>.pathInProjectFixture(path: Path): TestFixture<Path> {
  return testFixture {
    val project = init()
    val subpath = project.baseDir.toNioPath().resolve(path)
    initialized(subpath) {
      // will be removed with project directory
    }
  }
}

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
      manager.disposeModule(module)
    }
  }
}

/**
 * Create module on [pathFixture].
 * If [addPathToSourceRoot], we add [pathFixture] to the module sources,
 * which is convenient for the scripting languages where module root is also source root
 */
@TestOnly
fun TestFixture<Project>.moduleFixture(
  pathFixture: TestFixture<Path>,
  addPathToSourceRoot: Boolean = false,
): TestFixture<Module> = testFixture { _ ->
  val project = this@moduleFixture.init()
  val path = pathFixture.init()
  val manager = ModuleManager.getInstance(project)
  val module = edtWriteAction {
    manager.newModule(path, "")
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

@TestOnly
fun TestFixture<PsiDirectory>.psiFileFixture(
  name: String,
  content: String,
): TestFixture<PsiFile> = testFixture { _ ->
  val project = this@psiFileFixture.init().project
  val virtualFile = virtualFileFixture(name, content).init()
  val file = readAction {
    PsiManager.getInstance(project).findFile(virtualFile) ?: error("Fail to find file $virtualFile")
  }
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

@TestOnly
fun TestFixture<PsiFile>.editorFixture(): TestFixture<Editor> = testFixture { _ ->
  val psiFile = this@editorFixture.init()
  val project = psiFile.project
  val file = psiFile.virtualFile
  val editor = withContext(Dispatchers.EDT) {
    val fileEditorManager = project.serviceAsync<FileEditorManager>()
    writeIntentReadAction {
      val editor = fileEditorManager.openTextEditor(OpenFileDescriptor(project, file), true)
      requireNotNull(editor)
    }
  }
  initialized(editor) {
    withContext(Dispatchers.EDT) {
      project.serviceAsync<FileEditorManager>().closeFile(file)
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

@TestOnly
fun <T : Any> extensionPointFixture(epName: ExtensionPointName<in T>, createExtension: suspend () -> T): TestFixture<T> = testFixture {
  val extension = createExtension()
  val disposable = Disposer.newDisposable()
  epName.point.registerExtension(extension, disposable)
  initialized(extension) {
    Disposer.dispose(disposable)
  }
}
