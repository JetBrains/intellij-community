/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.refreshVfs
import com.intellij.util.SmartList
import com.intellij.util.io.*
import com.intellij.util.lang.CompoundRuntimeException
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.properties.Delegates

class TemporaryDirectory : ExternalResource() {
  private val paths = SmartList<Path>()

  private var sanitizedName: String by Delegates.notNull()

  override fun apply(base: Statement, description: Description): Statement {
    sanitizedName = sanitizeFileName(description.methodName)
    return super.apply(base, description)
  }

  override fun after() {
    val errors = SmartList<Throwable>()
    for (path in paths) {
      try {
        path.delete()
      }
      catch (e: Throwable) {
        errors.add(e)
      }
    }
    CompoundRuntimeException.throwIfNotEmpty(errors)
    paths.clear()
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

  fun newVirtualDirectory(directoryName: String? = null): VirtualFile {
    val path = generatePath(directoryName)
    path.createDirectories()
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.systemIndependentPath)
    VfsUtil.markDirtyAndRefresh(false, true, true, virtualFile)
    return virtualFile!!
  }
}

fun generateTemporaryPath(fileName: String?): Path {
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

fun VirtualFile.writeChild(relativePath: String, data: String) = VfsTestUtil.createFile(this, relativePath, data)