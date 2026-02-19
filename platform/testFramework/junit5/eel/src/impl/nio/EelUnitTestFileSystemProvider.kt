// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl.nio

import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider

internal class EelUnitTestFileSystemProvider(val defaultProvider: FileSystemProvider) : FileSystemProvider() {

  override fun getScheme(): String? {
    return defaultProvider.scheme
  }

  override fun newFileSystem(uri: URI, env: Map<String?, *>): FileSystem {
    throw UnsupportedOperationException("newFileSystem is not supported")
  }

  override fun getFileSystem(uri: URI): FileSystem? {
    throw UnsupportedOperationException("getFileSystem is not supported")
  }

  override fun getPath(uri: URI): Path {
    throw UnsupportedOperationException("getFileSystem is not supported")
  }

  override fun newByteChannel(path: Path, options: Set<OpenOption?>?, vararg attrs: FileAttribute<*>?): SeekableByteChannel? {
    val delegate = path.unfoldPath()
    return delegate.fileSystem.provider().newByteChannel(delegate, options, *attrs)
  }

  override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>?): DirectoryStream<Path?>? {
    val wrapper = dir.wrapper()
    val dirDelegate = dir.unfoldPath()
    val originalStream = dirDelegate.fileSystem.provider().newDirectoryStream(dirDelegate, {
      filter?.accept(wrapper(it)) ?: true
    })
    return object : DirectoryStream<Path?> {
      override fun iterator(): MutableIterator<Path?> {
        return originalStream.iterator().asSequence().map(wrapper).toMutableList().iterator()
      }

      override fun close() {
        originalStream.close()
      }
    }
  }

  override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>?) {
    val delegate = dir.unfoldPath()
    delegate.fileSystem.provider().createDirectory(delegate, *attrs)
  }

  override fun newFileChannel(path: Path, options: Set<OpenOption?>?, vararg attrs: FileAttribute<*>?): FileChannel? {
    val delegate = path.unfoldPath()
    return delegate.fileSystem.provider().newFileChannel(delegate, options, *attrs)
  }

  override fun delete(path: Path) {
    val delegate = path.unfoldPath()
    return delegate.fileSystem.provider().delete(delegate)
  }

  override fun copy(source: Path, target: Path, vararg options: CopyOption?) {
    val sourceDelegate = source.unfoldPath()
    val targetDelegate = target.unfoldPath()
    return sourceDelegate.fileSystem.provider().copy(sourceDelegate, targetDelegate, *options)
  }

  override fun move(source: Path, target: Path, vararg options: CopyOption?) {
    val sourceDelegate = source.unfoldPath()
    val targetDelegate = target.unfoldPath()
    return sourceDelegate.fileSystem.provider().move(sourceDelegate, targetDelegate, *options)
  }

  override fun isSameFile(path: Path, path2: Path): Boolean {
    val delegate1 = path.unfoldPath()
    val delegate2 = path2.unfoldPath()
    return delegate1.fileSystem.provider().isSameFile(delegate1, delegate2)
  }

  override fun isHidden(path: Path): Boolean {
    val delegate = path.unfoldPath()
    return delegate.fileSystem.provider().isHidden(delegate)
  }

  override fun getFileStore(path: Path): FileStore? {
    val delegate = path.unfoldPath()
    return delegate.fileSystem.provider().getFileStore(delegate)
  }

  override fun checkAccess(path: Path, vararg modes: AccessMode?) {
    val delegate = path.unfoldPath()
    return delegate.fileSystem.provider().checkAccess(delegate, *modes)
  }

  override fun <V : FileAttributeView?> getFileAttributeView(path: Path?, type: Class<V?>?, vararg options: LinkOption?): V? {
    val delegate = path!!.unfoldPath()
    return delegate.fileSystem.provider().getFileAttributeView(delegate, type, *options)
  }

  override fun <A : BasicFileAttributes?> readAttributes(path: Path, type: Class<A?>?, vararg options: LinkOption?): A? {
    val delegate = path.unfoldPath()
    return delegate.fileSystem.provider().readAttributes(delegate, type, *options)
  }

  override fun readAttributes(path: Path, attributes: String?, vararg options: LinkOption?): Map<String?, Any?>? {
    val delegate = path.unfoldPath()
    return delegate.fileSystem.provider().readAttributes(delegate, attributes, *options)
  }

  override fun setAttribute(path: Path, attribute: String?, value: Any?, vararg options: LinkOption?) {
    val delegate = path.unfoldPath()
    return delegate.fileSystem.provider().setAttribute(delegate, attribute, value, *options)
  }

  private fun Path.unfoldPath(): Path {
    return when (this) {
      is EelUnitTestPath -> this.delegate
      else -> throw InvalidPathException(this.toString(), "Incorrect input path: ${this.javaClass}")
    }
  }

  private fun Path.wrapper(): (Path) -> Path {
    when (this) {
      is EelUnitTestPath -> {
        val fs = this.fileSystem
        return { it -> EelUnitTestPath(fs, it) }
      }
      else -> throw InvalidPathException(this.toString(), "Incorrect input path: ${this.javaClass}")
    }
  }
}