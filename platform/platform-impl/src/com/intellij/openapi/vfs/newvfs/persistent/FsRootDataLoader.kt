// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.nio.file.Path

@Internal
interface FsRootDataLoader {
  val name: String

  @Throws(IOException::class)
  fun ensureLoaded(storage: Path)

  @Throws(IOException::class)
  fun deleteRootRecord(storage: Path, rootId: Int)

  @Throws(IOException::class)
  fun deleteDirectoryRecord(storage: Path, id: Int)

  @Throws(IOException::class)
  fun loadRootData(storage: Path, id: Int, path: String, fs: NewVirtualFileSystem)

  @Throws(IOException::class)
  fun loadDirectoryData(storage: Path, id: Int, parent: VirtualFile, childName: CharSequence, fs: NewVirtualFileSystem)
}

@Internal
class EmptyFsRootDataLoader : FsRootDataLoader {
  override val name: String = "empty"

  override fun ensureLoaded(storage: Path): Unit = Unit

  override fun deleteRootRecord(storage: Path, rootId: Int): Unit = Unit

  override fun deleteDirectoryRecord(storage: Path, id: Int): Unit = Unit

  override fun loadRootData(storage: Path, id: Int, path: String, fs: NewVirtualFileSystem): Unit = Unit

  override fun loadDirectoryData(storage: Path, id: Int, parent: VirtualFile, childName: CharSequence, fs: NewVirtualFileSystem): Unit = Unit
}