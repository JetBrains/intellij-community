// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import com.intellij.util.io.Ksuid
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.lang.CompoundRuntimeException
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.properties.Delegates

class TemporaryDirectory : ExternalResource() {
  private val paths = SmartList<Path>()
  private var sanitizedName: String by Delegates.notNull()

  companion object {
    @JvmStatic
    fun generateTemporaryPath(fileName: String): Path {
      // use unique postfix sortable by timestamp to avoid stale data in VFS and file exists check
      // (file is not created at the moment of path generation)
      val nameBuilder = StringBuilder()
      val extIndex = fileName.lastIndexOf('.')
      if (extIndex == -1) {
        nameBuilder.append(fileName)
      }
      else {
        nameBuilder.append(fileName, 0, extIndex)
      }
      nameBuilder.append('_')
      nameBuilder.append(Ksuid.generate())
      if (extIndex != -1) {
        nameBuilder.append(fileName, extIndex, fileName.length)
      }

      val path = Paths.get(FileUtilRt.getTempDirectory(), nameBuilder.toString())
      if (path.exists()) {
        throw IllegalStateException("Path $path must be unique but already exists")
      }
      return path
    }
  }

  override fun apply(base: Statement, description: Description): Statement {
    sanitizedName = sanitizeFileName(description.methodName)
    return super.apply(base, description)
  }

  override fun after() {
    if (paths.isEmpty()) {
      return
    }

    val errors = SmartList<Throwable>()
    for (path in paths) {
      try {
        path.delete()
      }
      catch (e: Throwable) {
        errors.add(e)
      }
    }

    paths.clear()

    if (errors.isNotEmpty()) {
      throw if (errors.size == 1) errors.first() else CompoundRuntimeException(errors)
    }
  }

  fun newPath(directoryName: String? = null, refreshVfs: Boolean = false): Path {
    val path = generatePath(directoryName)
    if (refreshVfs) {
      path.refreshVfs()
    }
    return path
  }

  private fun generatePath(suffix: String?): Path {
    var fileName = sanitizedName
    if (suffix != null) {
      fileName += "_$suffix"
    }

    val path = generateTemporaryPath(fileName)
    paths.add(path)
    return path
  }

  @JvmOverloads
  fun newVirtualDirectory(directoryName: String? = null): VirtualFile {
    val path = generatePath(directoryName)
    Files.createDirectories(path)
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
                      ?: throw java.lang.IllegalStateException("Cannot find virtual file by path: $path")
    VfsUtil.markDirtyAndRefresh(false, true, true, virtualFile)
    return virtualFile
  }
}

fun VirtualFile.writeChild(relativePath: String, data: String) = VfsTestUtil.createFile(this, relativePath, data)

fun VirtualFile.writeChild(relativePath: String, data: ByteArray) = VfsTestUtil.createFile(this, relativePath, data)

fun Path.refreshVfs() {
  // If a temp directory is reused from some previous test run, there might be cached children in its VFS. Ensure they're removed.
  val virtualFile = (LocalFileSystem.getInstance() ?: return).refreshAndFindFileByNioFile(this) ?: return
  VfsUtil.markDirtyAndRefresh(false, true, true, virtualFile)
}