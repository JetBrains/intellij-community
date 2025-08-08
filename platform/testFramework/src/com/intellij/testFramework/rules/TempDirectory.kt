// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.rules

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.VfsTestUtil
import com.intellij.util.io.jarFile
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * An improved variant of [org.junit.rules.TemporaryFolder] with lazy init, no symlinks in a temporary directory path,
 * better directory name, and more convenient [newFile], [newDirectory] methods.
 */
@Suppress("IO_FILE_USAGE")
open class TempDirectory : ExternalResource() {
  private var myName: String? = null
  private val myNextDirNameSuffix = AtomicInteger()
  private var myRoot: Path? = null
  private var myVirtualFileRoot: VirtualFile? = null

  /** Prefer [rootPath] */
  @get:ApiStatus.Obsolete
  val root: java.io.File
    get() = rootPath.toFile()

  val rootPath: Path
    get() {
      if (myRoot == null) {
        checkNotNull(myName) { "apply() was not called" }
        myRoot = Files.createTempDirectory(UsefulTestCase.TEMP_DIR_MARKER + myName + '_').toRealPath()
      }
      return myRoot!!
    }

  val virtualFileRoot: VirtualFile
    get() {
      if (myVirtualFileRoot == null) {
        myVirtualFileRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(rootPath)
        checkNotNull(myVirtualFileRoot) { "Cannot find virtual file by ${rootPath}" }
        VfsUtil.markDirtyAndRefresh(false, true, true, myVirtualFileRoot)
      }
      return myVirtualFileRoot!!
    }

  override fun apply(base: Statement, description: Description): Statement {
    before(description.methodName ?: description.className)
    return super.apply(base, description)
  }

  fun before(methodName: String) {
    myName = PlatformTestUtil.lowercaseFirstLetter(FileUtil.sanitizeFileName(methodName.take(30), true), true)
  }

  public override fun after() {
    val path = myRoot
    val vfsDir = myVirtualFileRoot

    myVirtualFileRoot = null
    myRoot = null
    myName = null

    RunAll(
      { JarFileSystemImpl.cleanupForNextTest() },
      { if (vfsDir != null) VfsTestUtil.deleteFile(vfsDir) },
      { if (path != null) NioFiles.deleteRecursively(path) }
    ).run()
  }

  /** Prefer [newDirectoryPath] */
  @ApiStatus.Obsolete
  fun newDirectory(): java.io.File = newDirectoryPath().toFile()

  /**
   * Creates a new directory with a random name under the root temp directory.
   */
  fun newDirectoryPath(): Path = newDirectoryPath("dir" + myNextDirNameSuffix.incrementAndGet())

  /** Prefer [newDirectoryPath] */
  @ApiStatus.Obsolete
  fun newDirectory(relativePath: String): java.io.File = newDirectoryPath(relativePath).toFile()

  /**
   * Creates a new directory at the given path relative to the root temp directory. Throws an exception if such a directory already exists.
   */
  fun newDirectoryPath(relativePath: String): Path {
    val dir = rootPath.resolve(relativePath)
    require(!Files.exists(dir)) { "Already exists: $dir" }
    makeDirectories(dir)
    return dir
  }

  /** Prefer [newFileNio] */
  @ApiStatus.Obsolete
  fun newFile(relativePath: String): java.io.File = newFileNio(relativePath).toFile()

  /** Prefer [newFileNio] */
  @ApiStatus.Obsolete
  fun newFile(relativePath: String, content: ByteArray?): java.io.File = newFileNio(relativePath, content).toFile()

  /**
   * Creates a new file with the given content at the given path relative to the root temp directory.
   * Throws an exception if such a file already exists.
   */
  @JvmOverloads
  fun newFileNio(relativePath: String, content: ByteArray? = null): Path {
    val file = rootPath.resolve(relativePath)
    require(!Files.exists(file)) { "Already exists: $file" }
    makeDirectories(file.parent)
    Files.createFile(file)
    if (content != null) {
      Files.write(file, content)
    }
    return file
  }

  /**
   * Creates a new virtual directory at the given path relative to the root temp directory. Throws an exception if such a directory already exists.
   */
  fun newVirtualDirectory(relativePath: String): VirtualFile {
    val existing = virtualFileRoot.findFileByRelativePath(relativePath)
    require(existing == null) { "Already exists: ${existing!!.path}"}
    return VfsTestUtil.createDir(virtualFileRoot, relativePath)
  }

  /**
   * Creates a new virtual file at the given path relative to the root temp directory. Throws an exception if such a file already exists.
   */
  fun newVirtualFile(relativePath: String): VirtualFile = newVirtualFile(relativePath, null)

  /**
   * Creates a new virtual file with the given content at the given path relative to the root temp directory.
   * Throws an exception if such a file already exists.
   */
  fun newVirtualFile(relativePath: String, content: ByteArray?): VirtualFile {
    val existing = virtualFileRoot.findFileByRelativePath(relativePath)
    require(existing == null) { "Already exists: ${existing!!.path}"}
    return VfsTestUtil.createFile(virtualFileRoot, relativePath, content)
  }

  /**
   * Creates a new empty JAR file at the given path relative to the root temp directory.
   */
  fun newEmptyVirtualJarFile(relativePath: String): VirtualFile {
    val existing = virtualFileRoot.findFileByRelativePath(relativePath)
    require(existing == null) { "Already exists: ${existing!!.path}"}
    val jarFile = rootPath.resolve(relativePath)
    jarFile { }.generate(jarFile)
    val localFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jarFile)!!
    return JarFileSystem.getInstance().getJarRootForLocalFile(localFile)!!
  }

  private fun makeDirectories(path: Path) {
    if (!Files.isDirectory(path)) {
      makeDirectories(path.parent)
      Files.createDirectory(path)
    }
  }
}

class TempDirectoryExtension : TempDirectory(), BeforeEachCallback, AfterEachCallback {
  override fun beforeEach(context: ExtensionContext) {
    before(context.displayName)
  }

  override fun afterEach(context: ExtensionContext) {
    after()
  }
}
