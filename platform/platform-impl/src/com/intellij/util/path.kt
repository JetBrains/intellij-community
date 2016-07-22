/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.io.readCharSequence
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

fun Path.exists() = Files.exists(this)

fun Path.createDirectories(): Path = Files.createDirectories(this)

/**
 * Opposite to Java, parent directories will be created
 */
fun Path.outputStream(): OutputStream {
  parent?.createDirectories()
  return Files.newOutputStream(this)
}

fun Path.inputStream(): InputStream = Files.newInputStream(this)

/**
 * Opposite to Java, parent directories will be created
 */
fun Path.createSymbolicLink(target: Path): Path {
  parent?.createDirectories()
  Files.createSymbolicLink(this, target)
  return this
}

fun Path.delete() {
  try {
    Files.delete(this)
  }
  catch (ignored: NoSuchFileException) {
  }
  catch (e: Exception) {
    FileUtil.delete(toFile())
  }
}

fun Path.deleteRecursively(): Path = if (exists()) Files.walkFileTree (this, object : SimpleFileVisitor<Path>() {
  override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
    file.delete()
    return FileVisitResult.CONTINUE
  }

  override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
    dir.delete()
    return FileVisitResult.CONTINUE
  }
}) else this

fun Path.lastModified(): FileTime = Files.getLastModifiedTime(this)

val Path.systemIndependentPath: String
  get() = toString().replace(File.separatorChar, '/')

val Path.parentSystemIndependentPath: String
  get() = parent!!.toString().replace(File.separatorChar, '/')

fun Path.readBytes(): ByteArray = Files.readAllBytes(this)

fun Path.readText(): String = readBytes().toString(Charsets.UTF_8)

fun Path.readChars() = inputStream().reader().readCharSequence(size().toInt())

fun Path.writeChild(relativePath: String, data: ByteArray) = resolve(relativePath).write(data)

fun Path.writeChild(relativePath: String, data: String) = writeChild(relativePath, data.toByteArray())

fun Path.write(data: ByteArray, offset: Int = 0, size: Int = data.size): Path {
  outputStream().use { it.write(data, offset, size) }
  return this
}

fun Path.write(data: String): Path {
  parent?.createDirectories()

  Files.write(this, data.toByteArray())
  return this
}

fun Path.size() = Files.size(this)

fun Path.basicAttributesIfExists(): BasicFileAttributes? {
  try {
    return Files.readAttributes(this, BasicFileAttributes::class.java)
  }
  catch (ignored: NoSuchFileException) {
    return null
  }
}

fun Path.sizeOrNull() = basicAttributesIfExists()?.size() ?: -1

fun Path.isHidden() = Files.isHidden(this)

fun Path.isDirectory() = Files.isDirectory(this)

fun Path.isFile() = Files.isRegularFile(this)

fun Path.move(target: Path): Path = Files.move(this, target)

/**
 * Opposite to Java, parent directories will be created
 */
fun Path.createFile() {
  parent?.createDirectories()
  Files.createFile(this)
}

fun Path.refreshVfs() {
  LocalFileSystem.getInstance()?.let { fs ->
    // If a temp directory is reused from some previous test run, there might be cached children in its VFS. Ensure they're removed.
    val virtualFile = fs.refreshAndFindFileByPath(systemIndependentPath)
    if (virtualFile != null) {
      VfsUtil.markDirtyAndRefresh(false, true, true, virtualFile)
    }
  }
}

inline fun <R> Path.directoryStreamIfExists(task: (stream: DirectoryStream<Path>) -> R): R? {
  try {
    return Files.newDirectoryStream(this).use(task)
  }
  catch (ignored: NoSuchFileException) {
  }
  return null
}

inline fun <R> Path.directoryStreamIfExists(noinline filter: ((path: Path) -> Boolean), task: (stream: DirectoryStream<Path>) -> R): R? {
  try {
    return Files.newDirectoryStream(this, filter).use(task)
  }
  catch (ignored: NoSuchFileException) {
  }
  return null
}