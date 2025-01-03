// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl.nio

import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

internal class EelUnitTestPath(val fileSystem: EelUnitTestFileSystem, val delegate: Path) : Path {

  init {
    require(delegate !is EelUnitTestPath)
  }

  override fun getFileSystem(): FileSystem {
    return fileSystem
  }

  override fun isAbsolute(): Boolean {
    return delegate.isAbsolute
  }

  override fun getRoot(): EelUnitTestPath {
    return fileSystem.root
  }

  override fun getFileName(): Path? {
    return EelUnitTestPath(fileSystem, delegate.fileName)
  }

  override fun getParent(): Path? {
    val parent = delegate.parent ?: return null
    return EelUnitTestPath(fileSystem, parent)
  }

  override fun getNameCount(): Int {
    if (delegate == fileSystem.root.delegate) {
      return 0
    }
    return delegate.nameCount - root.delegate.nameCount
  }

  override fun getName(index: Int): Path {
    return EelUnitTestPath(fileSystem, delegate.getName(index + root.delegate.nameCount))
  }

  override fun subpath(beginIndex: Int, endIndex: Int): Path {
    return EelUnitTestPath(fileSystem, delegate.subpath(beginIndex + root.delegate.nameCount, endIndex + root.delegate.nameCount))
  }

  override fun startsWith(other: Path): Boolean {
    require(other is EelUnitTestPath)
    return delegate.startsWith(other.delegate)
  }

  override fun endsWith(other: Path): Boolean {
    require(other is EelUnitTestPath)
    return delegate.endsWith(other.delegate)
  }

  override fun normalize(): Path {
    return EelUnitTestPath(fileSystem, delegate.normalize())
  }

  override fun resolve(other: Path): Path {
    require(other is EelUnitTestPath)
    return EelUnitTestPath(fileSystem, delegate.resolve(other.delegate))
  }

  override fun relativize(other: Path): Path {
    require(other is EelUnitTestPath)
    return EelUnitTestPath(fileSystem, delegate.relativize(other.delegate))
  }

  override fun toUri(): URI {
    TODO()
  }

  override fun toAbsolutePath(): Path {
    return EelUnitTestPath(fileSystem, delegate.toAbsolutePath())
  }

  override fun toRealPath(vararg options: LinkOption): Path {
    return EelUnitTestPath(fileSystem, delegate.toRealPath(*options))
  }

  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>?, vararg modifiers: WatchEvent.Modifier?): WatchKey {
    return delegate.register(watcher, events, *modifiers)
  }

  override fun compareTo(other: Path): Int {
    require(other is EelUnitTestPath)
    return delegate.compareTo(other.delegate)
  }

  override fun equals(other: Any?): Boolean {
    return other is EelUnitTestPath && other.fileSystem == fileSystem && other.delegate == delegate
  }

  override fun toString(): String {
    return if (isAbsolute) {
      if (delegate.nameCount == 0) {
        return fileSystem.fakeLocalRoot
      }
      else {
        fileSystem.fakeLocalRoot + delegate.toString().substring(fileSystem.rootDirectory.toString().length)
      }
    }
    else {
      delegate.toString()
    }
  }

  override fun hashCode(): Int {
    var result = fileSystem.hashCode()
    result = 31 * result + delegate.hashCode()
    return result
  }
}