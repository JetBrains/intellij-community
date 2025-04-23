// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl.nio

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.path.EelPath
import java.io.File
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

internal class EelUnitTestFileSystem(val provider: FileSystemProvider, val os: EelPlatform, val rootDirectory: Path, val fakeLocalRoot: String) : FileSystem() {
  val root: EelUnitTestPath = EelUnitTestPath(this, rootDirectory)

  override fun provider(): FileSystemProvider {
    return provider
  }

  override fun close() {
  }

  override fun isOpen(): Boolean {
    return true
  }

  override fun isReadOnly(): Boolean {
    return false
  }

  override fun getSeparator(): String? {
    // here we need to render separators as the IDE sees them, which means that we need OS-style separators
    return if (SystemInfo.isWindows) "\\" else "/"
  }

  override fun getRootDirectories(): Iterable<Path> {
    return listOf(root)
  }

  override fun getFileStores(): Iterable<FileStore?>? {
    TODO("Not yet implemented")
  }

  override fun supportedFileAttributeViews(): Set<String?>? {
    TODO("Not yet implemented")
  }

  override fun getPath(first: String, vararg more: String): Path {
    val remaining = if (first.startsWith(fakeLocalRoot)) {
      first.substringAfter(fakeLocalRoot)
    }
    else if (first.startsWith(fakeLocalRoot.replace(File.separatorChar, '/'))) {
      first.substringAfter(fakeLocalRoot.replace(File.separatorChar, '/'))
    }
    else {
      null
    }
    if (remaining != null) {
      if (remaining.isEmpty()) {
        return EelUnitTestPath(this, rootDirectory)
      }
      else {
        val parts = remaining.drop(1).split("/")
        val original = (parts + more).fold(rootDirectory, Path::resolve)
        return EelUnitTestPath(this, original)
      }
    }
    else {
      return EelUnitTestPath(this, rootDirectory.fileSystem.getPath(first, *more))
    }
  }

  override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher? {
    TODO("Not yet implemented")
  }

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService? {
    TODO("Not yet implemented")
  }

  override fun newWatchService(): WatchService? {
    TODO("Not yet implemented")
  }
}