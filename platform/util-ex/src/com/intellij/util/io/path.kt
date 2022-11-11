// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.util.io.NioFiles
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.*

fun Path.exists(): Boolean = Files.exists(this)

fun Path.createDirectories(): Path = NioFiles.createDirectories(this)

/**
 * Opposite to Java, parent directories will be created
 */
@JvmOverloads
fun Path.outputStream(append: Boolean = false): OutputStream {
  parent?.createDirectories()
  if (append) {
    return Files.newOutputStream(this, StandardOpenOption.APPEND, StandardOpenOption.CREATE)
  }
  return Files.newOutputStream(this)
}

fun Path.safeOutputStream(): OutputStream {
  parent?.createDirectories()
  return SafeFileOutputStream(this)
}

@Throws(IOException::class)
fun Path.inputStream(): InputStream = Files.newInputStream(this)

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
    return NioFiles.deleteRecursively(this)
  }
  else {
    return Files.delete(this)
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

@Throws(IOException::class)
fun Path.readBytes(): ByteArray = Files.readAllBytes(this)

@Throws(IOException::class)
fun Path.readText(): String = Files.readString(this)

fun Path.readChars(): CharSequence {
  // channel is used to avoid Files.size() call
  Files.newByteChannel(this).use { channel ->
    val size = channel.size().toInt()
    Channels.newReader(channel, Charsets.UTF_8.newDecoder(), size).use { reader ->
      return reader.readCharSequence(size)
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
fun Path.write(data: CharSequence, charset: Charset = Charsets.UTF_8, createParentDirs: Boolean = true): Path {
  if (createParentDirs) {
    parent?.createDirectories()
  }
  Files.writeString(this, data, charset)
  return this
}

@Throws(IOException::class)
fun Path.write(data: ByteBuffer, createParentDirs: Boolean = true): Path {
  if (createParentDirs) {
    parent?.createDirectories()
  }

  Files.newByteChannel(this, HashSet(listOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))).use {
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

fun Path.move(target: Path): Path {
  target.parent?.createDirectories()
  return Files.move(this, target, StandardCopyOption.REPLACE_EXISTING)
}

fun Path.copy(target: Path): Path {
  target.parent?.createDirectories()
  return Files.copy(this, target, StandardCopyOption.REPLACE_EXISTING)
}

fun Path.copyRecursively(target: Path) {
  target.parent?.createDirectories()
  Files.walk(this).use { stream ->
    stream.forEach { file ->
      Files.copy(file, target.resolve(this.relativize(file)))
    }
  }
}

/**
 * Opposite to Java, parent directories will be created
 */
fun Path.createFile(): Path {
  parent?.createDirectories()
  Files.createFile(this)
  return this
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

val Path.isWritable: Boolean
  get() = Files.isWritable(this)

fun isDirectory(attributes: BasicFileAttributes?): Boolean {
  return attributes != null && attributes.isDirectory
}

fun isSymbolicLink(attributes: BasicFileAttributes?): Boolean {
  return attributes != null && attributes.isSymbolicLink
}

fun Path.isAncestor(child: Path): Boolean = child.startsWith(this)

@Throws(IOException::class)
fun generateRandomPath(parentDirectory: Path): Path {
  var path = parentDirectory.resolve(UUID.randomUUID().toString())
  var i = 0
  while (path.exists() && i < 5) {
    path = parentDirectory.resolve(UUID.randomUUID().toString())
    ++i
  }
  if (path.exists()) {
    throw IOException("Couldn't generate unique random path.")
  }
  return path
}
