// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.containers.ContainerUtil
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.Channels
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlin.math.min

operator fun Path.div(x: String): Path = resolve(x)

operator fun File.div(x: String): File = File(this, x)

fun Path.exists(): Boolean = Files.exists(this)

fun Path.createDirectories(): Path {
  // symlink or existing regular file - Java SDK do this check, but with as `isDirectory(dir, LinkOption.NOFOLLOW_LINKS)`, i.e. links are not checked
  if (!Files.isDirectory(this)) {
    try {
      doCreateDirectories(toAbsolutePath())
    }
    catch (ignored: FileAlreadyExistsException) {
      // toAbsolutePath can return resolved path or file exists now
    }
  }
  return this
}

private fun doCreateDirectories(path: Path) {
  path.parent?.let {
    if (!Files.isDirectory(it)) {
      doCreateDirectories(it)
    }
  }
  Files.createDirectory(path)
}

/**
 * Opposite to Java, parent directories will be created
 */
fun Path.outputStream(): OutputStream {
  parent?.createDirectories()
  return Files.newOutputStream(this)
}

fun Path.safeOutputStream(): OutputStream {
  parent?.createDirectories()
  return SafeFileOutputStream(this)
}

@Throws(IOException::class)
fun Path.inputStream(): InputStream = Files.newInputStream(this)

@Throws(IOException::class)
fun Path.inputStreamSkippingBom() = CharsetToolkit.inputStreamSkippingBOM(inputStream().buffered())

fun Path.inputStreamIfExists(): InputStream? {
  try {
    return inputStream()
  }
  catch (e: NoSuchFileException) {
    return null
  }
}

/**
 * Opposite to Java, parent directories will be created
 */
fun Path.createSymbolicLink(target: Path): Path {
  parent?.createDirectories()
  Files.createSymbolicLink(this, target)
  return this
}

@JvmOverloads
fun Path.delete(recursively: Boolean = true) {
  if (recursively) {
    doDelete(this)
  }
  else {
    Files.delete(this)
  }
}

private fun doDelete(file: Path) {
  val attributes: BasicFileAttributes
  try {
    attributes = Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
  }
  catch (e: NoSuchFileException) {
    return
  }

  if (!attributes.isDirectory) {
    deleteFile(file)
    return
  }

  Files.walkFileTree(file, object : SimpleFileVisitor<Path>() {
    @Throws(IOException::class)
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      deleteFile(file)
      return FileVisitResult.CONTINUE
    }

    @Throws(IOException::class)
    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
      Files.deleteIfExists(dir)
      return FileVisitResult.CONTINUE
    }
  })
}

private fun deleteFile(file: Path) {
  try {
    Files.deleteIfExists(file)
  }
  catch (e: IOException) {
    // repeated delete is required for bad OS like Windows
    FileUtilRt.doIOOperation(FileUtilRt.RepeatableIOOperation<Boolean, IOException> { lastAttempt ->
      try {
        Files.deleteIfExists(file)
      }
      catch (e: IOException) {
        return@RepeatableIOOperation if (lastAttempt) {
          false
        }
        else {
          throw e
        }
      }

      true
    })
  }
}

fun Path.deleteWithParentsIfEmpty(root: Path, isFile: Boolean = true): Boolean {
  var parent = if (isFile) this.parent else null
  try {
    delete()
  }
  catch (e: NoSuchFileException) {
    return false
  }

  // remove empty directories
  while (parent != null && parent != root) {
    try {
      // must be only Files.delete, but not our delete (Files.delete throws DirectoryNotEmptyException)
      Files.delete(parent)
    }
    catch (e: IOException) {
      break
    }

    parent = parent.parent
  }

  return true
}

fun Path.deleteChildrenStartingWith(prefix: String) {
  directoryStreamIfExists({ it.fileName.toString().startsWith(prefix) }) { it.toList() }?.forEach {
    it.delete()
  }
}

fun Path.lastModified(): FileTime = Files.getLastModifiedTime(this)

val Path.systemIndependentPath: String
  get() = toString().replace(File.separatorChar, '/')

val Path.parentSystemIndependentPath: String
  get() = parent!!.toString().replace(File.separatorChar, '/')

@Throws(IOException::class)
fun Path.readBytes(): ByteArray = Files.readAllBytes(this)

@Throws(IOException::class)
fun Path.readText(): String = readBytes().toString(Charsets.UTF_8)

@Throws(IOException::class)
fun Path.readChars(): CharSequence {
  // channel is used to avoid Files.size() call
  Files.newByteChannel(this).use { channel ->
    val size = channel.size().toInt()
    Channels.newReader(channel, Charsets.UTF_8.newDecoder(), size).use { reader ->
      return reader.readCharSequence(channel.size().toInt())
    }
  }
}

@Throws(IOException::class)
fun Path.writeChild(relativePath: String, data: ByteArray) = resolve(relativePath).write(data)

@Throws(IOException::class)
fun Path.writeChild(relativePath: String, data: String) = writeChild(relativePath, data.toByteArray())

@Throws(IOException::class)
@JvmOverloads
fun Path.write(data: ByteArray, offset: Int = 0, size: Int = data.size): Path {
  outputStream().use { it.write(data, offset, size) }
  return this
}

@JvmOverloads
@Throws(IOException::class)
fun Path.write(data: CharSequence, createParentDirs: Boolean = true): Path {
  if (data is String) {
    if (createParentDirs) {
      parent?.createDirectories()
    }
    Files.write(this, data.toByteArray())
  }
  else {
    write(Charsets.UTF_8.encode(CharBuffer.wrap(data)), createParentDirs)
  }
  return this
}

@Throws(IOException::class)
fun Path.write(data: ByteBuffer, createParentDirs: Boolean = true): Path {
  if (createParentDirs) {
    parent?.createDirectories()
  }

  Files.newByteChannel(this, setOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)).use {
    it.write(data)
  }
  return this
}

fun Path.size(): Long = Files.size(this)

fun Path.basicAttributesIfExists(): BasicFileAttributes? {
  try {
    return Files.readAttributes(this, BasicFileAttributes::class.java)
  }
  catch (ignored: FileSystemException) {
    return null
  }
}

fun Path.sizeOrNull(): Long = basicAttributesIfExists()?.size() ?: -1

fun Path.isHidden(): Boolean = Files.isHidden(this)

fun Path.isDirectory(): Boolean = Files.isDirectory(this)

fun Path.isFile(): Boolean = Files.isRegularFile(this)

fun Path.move(target: Path): Path = Files.move(this, target, StandardCopyOption.REPLACE_EXISTING)

fun Path.copy(target: Path): Path {
  target.parent?.createDirectories()
  return Files.copy(this, target, StandardCopyOption.REPLACE_EXISTING)
}

/**
 * Opposite to Java, parent directories will be created
 */
fun Path.createFile() {
  parent?.createDirectories()
  Files.createFile(this)
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
    return Files.newDirectoryStream(this, DirectoryStream.Filter { filter(it) }).use(task)
  }
  catch (ignored: NoSuchFileException) {
  }
  return null
}

private val illegalChars = ContainerUtil.set('/', '\\', '?', '<', '>', ':', '*', '|', '"', ':')

// https://github.com/parshap/node-sanitize-filename/blob/master/index.js
fun sanitizeFileName(name: String, replacement: String? = "_", isTruncate: Boolean = true): String {
  var result: StringBuilder? = null
  var last = 0
  val length = name.length
  for (i in 0 until length) {
    val c = name[i]
    if (!illegalChars.contains(c) && !c.isISOControl()) {
      continue
    }

    if (result == null) {
      result = StringBuilder()
    }
    if (last < i) {
      result.append(name, last, i)
    }

    if (replacement != null) {
      result.append(replacement)
    }
    last = i + 1
  }

  fun String.truncateFileName() = if (isTruncate) substring(0, min(length, 255)) else this

  if (result == null) {
    return name.truncateFileName()
  }

  if (last < length) {
    result.append(name, last, length)
  }

  return result.toString().truncateFileName()
}

val Path.isWritable: Boolean
  get() = Files.isWritable(this)

fun isDirectory(attributes: BasicFileAttributes?): Boolean {
  return attributes != null && attributes.isDirectory
}

fun isSymbolicLink(attributes: BasicFileAttributes?): Boolean {
  return attributes != null && attributes.isSymbolicLink
}