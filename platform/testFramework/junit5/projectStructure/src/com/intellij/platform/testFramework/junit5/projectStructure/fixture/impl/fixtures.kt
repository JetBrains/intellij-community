// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
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
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString
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

@TestOnly
internal fun dirFixture(dir: Path): TestFixture<Path> = testFixture("dirFixture") {
  val tempDir = withContext(Dispatchers.IO) {
    if (!dir.exists()) {
      dir.createDirectories()
    }
    dir
  }
  initialized(tempDir) {
    withContext(Dispatchers.IO) {
      if (tempDir.exists()) {
        tempDir.delete(recursively = true)
      }
    }
  }
}

@TestOnly
internal fun TestFixture<Path>.subDirFixture(name: String): TestFixture<Path> = testFixture("subDirFixture") {
  val path = this@subDirFixture.init().resolve(name)
  val result = dirFixture(path).init()
  initialized(result) {}
}

@TestOnly
internal fun TestFixture<Path>.fileFixture(fileName: String, content: CharSequence): TestFixture<Path> =
  fileFixture(fileName) { it.writeText(content) }

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


@TestOnly
internal fun TestFixture<Project>.sdkFixture(name: String, type: SdkTypeId, pathFixture: TestFixture<Path>): TestFixture<Sdk> = testFixture("sdkFixture $name") {
  val project = this@sdkFixture.init()
  val jdkTable = ProjectJdkTable.getInstance(project)
  val homePath = pathFixture.init().pathString
  val sdk = jdkTable.createSdk(name, type)
  val root = requireNotNull(VfsUtil.findFile(Path(homePath), true))
  edtWriteAction {
    val sdkModificator = sdk.sdkModificator
    sdkModificator.homePath = homePath
    sdkModificator.addRoot(root, OrderRootType.CLASSES)
    sdkModificator.commitChanges()
    jdkTable.addJdk(sdk)
  }
  initialized(sdk) {
    writeAction {
      ProjectJdkTable.getInstance(project).removeJdk(sdk)
    }
  }
}

private fun getSourceRootType(isTestSource: Boolean, isResource: Boolean): JpsModuleSourceRootType<*> = when {
  isTestSource && isResource -> JavaResourceRootType.TEST_RESOURCE
  !isTestSource && isResource -> JavaResourceRootType.RESOURCE
  isTestSource && !isResource -> JavaSourceRootType.TEST_SOURCE
  else -> JavaSourceRootType.SOURCE
}
