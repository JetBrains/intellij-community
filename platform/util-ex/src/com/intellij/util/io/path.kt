// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.nio.file.DirectoryStream
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** See [NioFiles.createDirectories]. */
fun Path.createDirectories(): Path = NioFiles.createDirectories(this)

/** See [NioFiles.createParentDirectories] */
fun Path.createParentDirectories(): Path = NioFiles.createParentDirectories(this)

/**
 * Opposite to Java, parent directories will be created
 */
fun Path.outputStream(append: Boolean): OutputStream {
  parent?.createDirectories()
  if (append) {
    return Files.newOutputStream(this, StandardOpenOption.APPEND, StandardOpenOption.CREATE)
  }
  else {
    return Files.newOutputStream(this)
  }
}

/**
 * Opposite to Java, parent directories will be created
 */
fun Path.outputStream(): OutputStream {
  parent?.createDirectories()
  return Files.newOutputStream(this)
}

fun Path.safeOutputStream(): OutputStream = SafeFileOutputStream(this.createParentDirectories())

fun Path.inputStreamIfExists(): InputStream? = try {
  inputStream()
}
catch (_: NoSuchFileException) {
  null
}

@JvmOverloads
fun Path.delete(recursively: Boolean = true) {
  when {
    recursively -> NioFiles.deleteRecursively(this)
    else -> Files.delete(this)
  }
}

fun Path.deleteWithParentsIfEmpty(root: Path, isFile: Boolean = true): Boolean {
  var parent = if (isFile) this.parent else null
  try {
    delete()
  }
  catch (_: NoSuchFileException) {
    return false
  }

  // remove empty directories
  while (parent != null && parent != root) {
    try {
      // must be only Files.delete, but not our `delete` (Files.delete throws DirectoryNotEmptyException)
      Files.delete(parent)
    }
    catch (_: IOException) {
      break
    }

    parent = parent.parent
  }

  return true
}

@get:ApiStatus.Obsolete
val Path.systemIndependentPath: String
  get() = invariantSeparatorsPathString

fun Path.readChars(): CharSequence {
  // a channel is used to avoid Files.size() call
  Files.newByteChannel(this).use { channel ->
    val size = channel.size().toInt()
    Channels.newReader(channel, Charsets.UTF_8.newDecoder(), size).use { reader ->
      return reader.readCharSequence(size)
    }
  }
}

@Throws(IOException::class)
fun Path.write(data: ByteArray, offset: Int, size: Int): Path {
  parent?.createDirectories()
  Files.newOutputStream(this).use { it.write(data, offset, size) }
  return this
}

@Throws(IOException::class)
fun Path.write(data: ByteArray): Path {
  parent?.createDirectories()
  Files.write(this, data)
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

fun Path.basicAttributesIfExists(): BasicFileAttributes? = try {
  Files.readAttributes(this, BasicFileAttributes::class.java)
}
catch (_: FileSystemException) {
  null
}

fun Path.fileSizeSafe(fallback: Long = 0): Long = try {
  fileSize()
}
catch (_: FileSystemException) {
  fallback
}

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

inline fun <R> Path.directoryStreamIfExists(task: (stream: DirectoryStream<Path>) -> R): R? = try {
  Files.newDirectoryStream(this).use(task)
}
catch (_: NoSuchFileException) {
  null
}

inline fun <R> Path.directoryStreamIfExists(noinline filter: ((path: Path) -> Boolean), task: (stream: DirectoryStream<Path>) -> R): R? = try {
  Files.newDirectoryStream(this, makeFilter(filter)).use(task)
}
catch (_: NoSuchFileException) {
  null
}

// extracted to not introduce additional JVM class for every directoryStreamIfExists call site
@PublishedApi
internal fun makeFilter(filter: (path: Path) -> Boolean): DirectoryStream.Filter<Path> = DirectoryStream.Filter { filter(it) }

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

@Deprecated(message = "Use kotlin.io.path.readText", level = DeprecationLevel.ERROR)
@Suppress("DeprecatedCallableAddReplaceWith")
@Throws(IOException::class)
fun Path.readText(): String = readText()

@Deprecated(message = "Use kotlin.io.path.exists", level = DeprecationLevel.ERROR)
@Suppress("DeprecatedCallableAddReplaceWith")
fun Path.exists(): Boolean = exists()

@Deprecated(message = "Use kotlin.io.path.isDirectory", level = DeprecationLevel.ERROR)
@Suppress("DeprecatedCallableAddReplaceWith")
fun Path.isDirectory(): Boolean = isDirectory()

@Deprecated(message = "Use kotlin.io.path.isRegularFile", level = DeprecationLevel.ERROR)
@Suppress("DeprecatedCallableAddReplaceWith")
fun Path.isFile(): Boolean = isRegularFile()

@Deprecated(message = "Trivial, just inline", level = DeprecationLevel.ERROR)
@Suppress("DeprecatedCallableAddReplaceWith")
fun Path.isAncestor(child: Path): Boolean = child.startsWith(this)

@Deprecated(message = "Use kotlin.io.path.inputStream", level = DeprecationLevel.ERROR)
@Suppress("DeprecatedCallableAddReplaceWith")
@Throws(IOException::class)
fun Path.inputStream(): InputStream = inputStream()

@Deprecated(message = "Use `kotlin.io.path.createFile` with `com.intellij.util.io.createParentDirectories`", level = DeprecationLevel.ERROR)
@Suppress("DeprecatedCallableAddReplaceWith")
fun Path.createFile(): Path = this.createParentDirectories().createFile()
//</editor-fold>
