// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
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
internal fun TestFixture<Module>.customContentRootFixture(
  pathFixture: TestFixture<Path>,
): TestFixture<Path> = testFixture("customContentRootFixture") {
  val module = this@customContentRootFixture.init()
  val path = pathFixture.init()
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
  pathFixture: TestFixture<Path>,
  contentRootFixture: TestFixture<Path>,
  isTestSource: Boolean = false,
  isResource: Boolean = false,
): TestFixture<Path> = testFixture("customSourceRootFixture") {
  val path = pathFixture.init()
  val module = this@customSourceRootFixture.init()
  val contentRootPath = contentRootFixture.init()
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

private suspend fun createDirectory(dir: Path) {
  withContext(Dispatchers.IO) {
    if (!dir.exists()) {
      dir.createDirectories()
    }
  }
}

private suspend fun deleteDirectory(dir: Path) {
  withContext(Dispatchers.IO) {
    if (dir.exists()) {
      dir.delete(recursively = true)
    }
  }
}

@TestOnly
internal fun dirFixture(dir: Path, vararg dependencies: TestFixture<*>): TestFixture<Path> = testFixture("dirFixture") {
  dependencies.forEach { it.init() }
  createDirectory(dir)
  initialized(dir) {
    deleteDirectory(dir)
  }
}

@TestOnly
internal fun TestFixture<Path>.subDirFixture(name: String): TestFixture<Path> = testFixture("subDirFixture") {
  val path = this@subDirFixture.init().resolve(name)
  createDirectory(path)
  initialized(path) {
    deleteDirectory(path)
  }
}

@TestOnly
internal fun TestFixture<Path>.fileFixture(fileName: String, content: CharSequence): TestFixture<Path> =
  fileFixture(fileName) { it.writeText(content) }

@TestOnly
fun TestFixture<Path>.executableFileFixture(fileName: String, content: CharSequence): TestFixture<Path> =
  fileFixture(fileName) {
    it.writeText(content)
    NioFiles.setExecutable(it)
  }

@TestOnly
internal fun TestFixture<Path>.fileFixture(fileName: String, content: ByteArray): TestFixture<Path> =
  fileFixture(fileName) { it.write(content) }

@TestOnly
private fun TestFixture<Path>.fileFixture(fileName: String, content: (file: Path) -> Unit): TestFixture<Path> = testFixture("fileFixture") {
  val file = withContext(Dispatchers.IO) {
    val dir = this@fileFixture.init()
    if (!dir.exists()) {
      dir.createDirectories()
    }
    val file = dir.resolve(fileName)
    content(file)
    file
  }
  initialized(file) {
    withContext(Dispatchers.IO) {
      if (file.exists()) {
        file.delete()
      }
    }
  }
}




private fun getSourceRootType(isTestSource: Boolean, isResource: Boolean): JpsModuleSourceRootType<*> = when {
  isTestSource && isResource -> JavaResourceRootType.TEST_RESOURCE
  !isTestSource && isResource -> JavaResourceRootType.RESOURCE
  isTestSource && !isResource -> JavaSourceRootType.TEST_SOURCE
  else -> JavaSourceRootType.SOURCE
}
