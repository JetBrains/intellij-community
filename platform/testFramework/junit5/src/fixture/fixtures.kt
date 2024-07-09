// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path

@TestOnly
fun tempPathFixture(root: Path? = null): TestFixture<Path> = testFixture {
  val tempDir = withContext(Dispatchers.IO) {
    if (root == null) {
      Files.createTempDirectory("IJ")
    }
    else {
      Files.createTempDirectory(root, "IJ")
    }
  }
  initialized(tempDir) {
    withContext(Dispatchers.IO) {
      Files.deleteIfExists(tempDir)
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
