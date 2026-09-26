// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.io.createDirectories
import com.intellij.util.io.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

@TestOnly
internal fun TestFixture<Project>.customModuleFixture(
  path: Path,
): TestFixture<Module> = testFixture("customModuleFixture") {
  val project = this@customModuleFixture.init()
  val manager = ModuleManager.getInstance(project)
  val module = edtWriteAction {
    manager.newModule(path, "")
  }
  initialized(module) {
    edtWriteAction {
      manager.disposeModule(module)
    }
  }
}

@TestOnly
internal fun TestFixture<Module>.customContentRootFixture(
  path: Path,
): TestFixture<Path> = testFixture("customContentRootFixture") {
  val module = this@customContentRootFixture.init()
  val dir = VfsUtil.findFile(path, true) ?: error("Failed to find VFS file for path: $path")
  edtWriteAction {
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.addContentEntry(dir)
    }
  }
  initialized(path) {}
}

@TestOnly
internal fun TestFixture<Module>.customSourceRootFixture(
  path: Path,
  contentRootPath: Path,
  isTestSource: Boolean = false,
  isResource: Boolean = false,
): TestFixture<Path> = testFixture("customSourceRootFixture") {
  val module = this@customSourceRootFixture.init()
  edtWriteAction {
    val dir = VfsUtil.findFile(path, true) ?: error("Failed to find VFS file for path: $path")
    ModuleRootModificationUtil.updateModel(module) { model ->
      val contentEntry = model.contentEntries
                           .find { it.file?.toNioPath() == contentRootPath }
                         ?: error("Content entry with path '$path' was not found in module ${module.name}.")
      val type = getSourceRootType(isTestSource, isResource)
      contentEntry.addSourceFolder(dir, type)
    }
  }
  initialized(path) {}
}

internal suspend fun createDirectory(dir: Path): Path {
  withContext(Dispatchers.IO) {
    if (!dir.exists()) {
      dir.createDirectories()
    }
  }
  return dir
}

internal suspend fun Path.writeFile(fileName: String, content: CharSequence): Path =
  writeFile(fileName) { it.writeText(content) }

internal suspend fun Path.writeFile(fileName: String, content: ByteArray): Path =
  writeFile(fileName) { it.write(content) }

private suspend fun Path.writeFile(fileName: String, content: (file: Path) -> Unit): Path {
  val file = resolve(fileName).normalize()
  assert(file.startsWith(this)) {
    "'$fileName' is outside of '$this': this fixture does not support such paths because of the cleanup implementation"
  }
  createDirectory(file.parent)
  withContext(Dispatchers.IO) {
    content(file)
  }
  return file
}

@TestOnly
suspend fun Path.writeExecutableFile(fileName: String, content: CharSequence): Path =
  writeFile(fileName) {
    it.writeText(content)
    NioFiles.setExecutable(it)
  }


private fun getSourceRootType(isTestSource: Boolean, isResource: Boolean): JpsModuleSourceRootType<*> = when {
  isTestSource && isResource -> JavaResourceRootType.TEST_RESOURCE
  !isTestSource && isResource -> JavaResourceRootType.RESOURCE
  isTestSource && !isResource -> JavaSourceRootType.TEST_SOURCE
  else -> JavaSourceRootType.SOURCE
}
