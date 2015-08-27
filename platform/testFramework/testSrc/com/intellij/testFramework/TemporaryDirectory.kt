/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

public class TemporaryDirectory : ExternalResource() {
  private val paths = SmartList<Path>()

  private var sanitizedName: String? = null

  override fun apply(base: Statement, description: Description): Statement {
    sanitizedName = FileUtil.sanitizeFileName(description.getMethodName(), false)
    return super.apply(base, description)
  }

  override fun after() {
    for (path in paths) {
      path.deleteRecursively()
    }
    paths.clear()
  }

  /**
   * Directory is not created.
   */
  public fun newDirectory(directoryName: String? = null): File = generatePath(directoryName).toFile()

  public fun newPath(directoryName: String? = null, refreshVfs: Boolean = true): Path {
    val path = generatePath(directoryName)
    if (refreshVfs) {
      path.refreshVfs()
    }
    return path
  }

  private fun generatePath(suffix: String?): Path {
    var fileName = sanitizedName!!
    if (suffix != null) {
      fileName += "_$suffix"
    }

    var path = generateTemporaryPath(fileName)
    paths.add(path)
    return path
  }

  public fun newVirtualDirectory(directoryName: String? = null): VirtualFile {
    val path = generatePath(directoryName)
    path.createDirectories()
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.systemIndependentPath)
    VfsUtil.markDirtyAndRefresh(false, true, true, virtualFile)
    return virtualFile!!
  }
}

public fun generateTemporaryPath(fileName: String?): Path {
  val tempDirectory = Paths.get(FileUtilRt.getTempDirectory())
  var path = tempDirectory.resolve(fileName)
  var i = 0
  while (path.exists() && i < 9) {
    path = tempDirectory.resolve("${fileName}_$i")
    i++
  }

  if (path.exists()) {
    throw IOException("Cannot generate unique random path")
  }
  return path
}

public fun Path.exists(): Boolean = Files.exists(this)

public fun Path.createDirectories(): Path = Files.createDirectories(this)

public fun Path.deleteRecursively(): Path = if (exists()) Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
  override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
    Files.delete(file)
    return FileVisitResult.CONTINUE
  }

  override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
    Files.delete(dir)
    return FileVisitResult.CONTINUE
  }
}) else this

public val Path.systemIndependentPath: String
  get() = toString().replace(File.separatorChar, '/')

public val Path.parentSystemIndependentPath: String
  get() = getParent()!!.toString().replace(File.separatorChar, '/')

public fun Path.readText(): String = Files.readAllBytes(this).toString(Charsets.UTF_8)

public fun VirtualFile.writeChild(relativePath: String, data: String): VirtualFile = VfsTestUtil.createFile(this, relativePath, data)

public fun Path.writeChild(relativePath: String, data: String): Path = writeChild(relativePath, data.toByteArray())

public fun Path.writeChild(relativePath: String, data: ByteArray): Path {
  val path = resolve(relativePath)
  path.getParent().createDirectories()
  return Files.write(path, data)
}

public fun Path.isDirectory(): Boolean = Files.isDirectory(this)

public fun Path.isFile(): Boolean = Files.isRegularFile(this)

/**
 * Opposite to ugly Java, parent directories will be created
 */
public fun Path.createFile() {
  getParent()?.createDirectories()
  Files.createFile(this)
}

public fun Path.refreshVfs() {
  LocalFileSystem.getInstance()?.let { fs ->
    // If a temp directory is reused from some previous test run, there might be cached children in its VFS. Ensure they're removed.
    val virtualFile = fs.findFileByPath(systemIndependentPath)
    if (virtualFile != null) {
      VfsUtil.markDirtyAndRefresh(false, true, true, virtualFile)
    }
  }
}

val VirtualFile.path: String
  get() = getPath()