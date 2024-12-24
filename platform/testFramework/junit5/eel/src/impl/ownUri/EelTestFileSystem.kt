// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.impl.ownUri

import com.intellij.platform.testFramework.junit5.eel.impl.FAKE_WINDOWS_ROOT
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

internal class EelTestFileSystem(
  val provider: EelTestFileSystemProvider,
  val fsId: String,
  val rootDirectory: Path,
  val fakeLocalRoot: String,
) : FileSystem() {
  init {
    require(rootDirectory !is EelTestPath)
  }

  val root: EelTestPath = EelTestPath(this, rootDirectory)

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
    return "/"
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

  override fun getPath(first: String, vararg more: String): EelTestPath {
    if (first.startsWith(FAKE_WINDOWS_ROOT) || first.startsWith("/")) {
      val parts = first.split("/")
      val original = (parts + more).fold(rootDirectory, Path::resolve)
      return EelTestPath(this, original)
    }
    else {
      return EelTestPath(this, rootDirectory.fileSystem.getPath(first, *more))
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