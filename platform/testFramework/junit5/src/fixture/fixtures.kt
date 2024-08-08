// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VfsUtil
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
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

@TestOnly
fun tempPathFixture(root: Path? = null): TestFixture<Path> = testFixture {
  val tempDir = withContext(Dispatchers.IO) {
    if (root == null) {
      Files.createTempDirectory("IJ")
    }
    else {
      if (!root.exists()) {
        root.createDirectories()
      }
      Files.createTempDirectory(root, "IJ")
    }
  }
  initialized(tempDir) {
    withContext(Dispatchers.IO) {
      tempDir.delete(recursively = true)
    }
  }
}

@TestOnly
fun projectFixture(
  pathFixture: TestFixture<Path> = tempPathFixture(null),
  openProjectTask: OpenProjectTask = OpenProjectTask.build(),
): TestFixture<Project> = testFixture {
  val path = pathFixture.init()
  val project = ProjectManagerEx.getInstanceEx().newProjectAsync(path, openProjectTask)
  initialized(project) {
    ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project, save = false)
  }
}

@TestOnly
fun TestFixture<Project>.moduleFixture(
  name: String? = null,
): TestFixture<Module> = testFixture(name ?: "unnamed module") { id ->
  val project = this@moduleFixture.init()
  val manager = ModuleManager.getInstance(project)
  val module = writeAction {
    manager.newNonPersistentModule(name ?: id, "")
  }
  initialized(module) {
    writeAction {
      manager.disposeModule(module)
    }
  }
}

@TestOnly
fun TestFixture<Project>.moduleFixture(
  pathFixture: TestFixture<Path>,
): TestFixture<Module> = testFixture { _ ->
  val project = this@moduleFixture.init()
  val path = pathFixture.init()
  val manager = ModuleManager.getInstance(project)
  val module = writeAction {
    manager.newModule(path, "")
  }
  initialized(module) {
    writeAction {
      manager.disposeModule(module)
    }
  }
}

@TestOnly
fun disposableFixture(): TestFixture<Disposable> = testFixture { debugString ->
  val disposable = Disposer.newCheckedDisposable(debugString)
  initialized(disposable) {
    Disposer.dispose(disposable)
  }
}

@TestOnly
fun TestFixture<Module>.sourceRootFixture(isTestSource: Boolean = false, pathFixture: TestFixture<Path> = tempPathFixture()): TestFixture<PsiDirectory> =
  testFixture { _ ->
    val module = this@sourceRootFixture.init()
    val directoryPath: Path = pathFixture.init()
    val directoryVfs = VfsUtil.createDirectories(directoryPath.toCanonicalPath())
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.addContentEntry(directoryVfs).addSourceFolder(directoryVfs, isTestSource)
    }
    val directory = readAction {
      PsiManager.getInstance(module.project).findDirectory(directoryVfs) ?: error("Fail to find directory $directoryVfs")
    }
    initialized(directory) {
      writeAction {
        directory.delete()
      }
    }
  }

@TestOnly
fun TestFixture<PsiDirectory>.psiFileFixture(
  name: String,
  content: String,
): TestFixture<PsiFile> = testFixture { _ ->
  val sor = this@psiFileFixture.init()
  val file = writeAction {
    sor.createFile(name).also {
      it.virtualFile.setBinaryContent(content.toByteArray())
    }
  }
  initialized(file) {
    writeAction {
      file.delete()
    }
  }
}