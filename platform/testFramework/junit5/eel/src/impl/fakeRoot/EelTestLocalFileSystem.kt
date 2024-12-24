// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl.fakeRoot

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.testFramework.junit5.eel.impl.ownUri.EelTestFileSystemProvider
import com.intellij.platform.testFramework.junit5.eel.impl.ownUri.EelTestPath
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

internal class EelTestLocalFileSystem(val provider: EelTestFileSystemProvider, val fsId: String, val os: EelPath.OS, val rootDirectory: EelTestPath, val fakeLocalRoot: String) : FileSystem() {
  val root: EelTestLocalPath = EelTestLocalPath(this, rootDirectory)

  override fun provider(): FileSystemProvider {
    return provider
  }

  override fun close() {
    provider.fileSystems.remove(fsId)
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
    if (first.startsWith(fakeLocalRoot)) {
      val remaining = first.substringAfter(fakeLocalRoot)
      if (remaining.isEmpty()) {
        return EelTestLocalPath(this, rootDirectory)
      }
      else {
        val parts = remaining.drop(1).split("/")
        val original = (parts + more).fold(rootDirectory, EelTestPath::resolve)
        return EelTestLocalPath(this, original)

      }
    }
    else {
      return EelTestLocalPath(this, rootDirectory.fileSystem.getPath(first, *more))
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