// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.rules

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.VfsTestUtil
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

/**
 * An improved variant of [org.junit.rules.TemporaryFolder] with lazy init, no symlinks in a temporary directory path, better directory name,
 * and more convenient [newFile], [newDirectory] methods.
 */
class TempDirectory : ExternalResource() {
  private var name: String? = null
  private val nextDirNameSuffix = AtomicInteger()
  private var myRoot: File? = null
  private var myVirtualFileRoot: VirtualFile? = null

  val root: File
    get() {
      if (myRoot == null) {
        checkNotNull(name) { "apply() was not called" }
        myRoot = Files.createTempDirectory(UsefulTestCase.TEMP_DIR_MARKER + name + '_').toRealPath().toFile()
      }
      return myRoot!!
    }

  val rootPath: Path
    get() = root.toPath()

  private val virtualFileRoot: VirtualFile
    get() {
      if (myVirtualFileRoot == null) {
        myVirtualFileRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root)
        checkNotNull(myVirtualFileRoot) { "Cannot find virtual file by $root" }
        VfsUtil.markDirtyAndRefresh(false, true, true, myVirtualFileRoot)
      }
      return myVirtualFileRoot!!
    }

  override fun apply(base: Statement,
                     description: Description): Statement {
    name = PlatformTestUtil.lowercaseFirstLetter(FileUtil.sanitizeFileName(description.methodName, false), true)
    return super.apply(base, description)
  }

  override fun after() {
    if (myRoot != null) {
      val path = myRoot!!.toPath()
      myVirtualFileRoot = null
      myRoot = null
      name = null
      FileUtil.delete(path)
    }
  }

  /**
   * Creates a new directory with the given relative path from the root temp directory. Throws an exception if such a directory already exists.
   */
  fun newDirectory(relativePath: String): File {
    val dir = Paths.get(root.path, relativePath)
    require(!Files.exists(dir)) { "Already exists: $dir" }
    makeDirectories(dir)
    return dir.toFile()
  }

  /**
   * Creates a new directory with random name under the root temp directory.
   */
  fun newDirectory(): File {
    return FileUtil.createTempDirectory(root, "dir" + nextDirNameSuffix.incrementAndGet(), null)
  }

  /**
   * Creates a new file with the given relative path from the root temp directory. Throws an exception if such a file already exists.
   */
  @JvmOverloads
  fun newFile(relativePath: String, content: ByteArray? = null): File {
    val file = Paths.get(root.path, relativePath)
    require(!Files.exists(file)) { "Already exists: $file" }
    makeDirectories(file.parent)
    Files.createFile(file)
    if (content != null) {
      Files.write(file, content)
    }
    return file.toFile()
  }

  /**
   * Creates a new virtual directory with the given relative path from the root temp directory. Throws an exception if such a directory already exists.
   */
  fun newVirtualDirectory(relativePath: String): VirtualFile {
    val existing = virtualFileRoot.findFileByRelativePath(relativePath)
    require(existing == null) { "Already exists: ${existing!!.path}"}
    return VfsTestUtil.createDir(virtualFileRoot, relativePath)
  }

  /**
   * Creates a new virtual file with the given relative path from the root temp directory. Throws an exception if such a file already exists.
   */
  fun newVirtualFile(relativePath: String, content: String = ""): VirtualFile {
    val existing = virtualFileRoot.findFileByRelativePath(relativePath)
    require(existing == null) { "Already exists: ${existing!!.path}"}
    return VfsTestUtil.createFile(virtualFileRoot, relativePath, content)
  }

  @Deprecated("use newDirectory(relativePath) instead", ReplaceWith("newDirectory(relativePath)"))
  fun newFolder(relativePath: String): File {
    return newDirectory(relativePath)
  }

  @Deprecated("use newDirectory() instead", ReplaceWith("newDirectory()"))
  fun newFolder(): File {
    return newDirectory()
  }

  private fun makeDirectories(path: Path) {
    if (!Files.isDirectory(path)) {
      makeDirectories(path.parent)
      Files.createDirectory(path)
    }
  }
}