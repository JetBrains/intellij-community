// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.nio.file.Path

@Internal
interface FsRootDataLoader {
  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<FsRootDataLoader> = ExtensionPointName<FsRootDataLoader>("com.intellij.fsRootDataLoader")
  }

  val name: String

  @Throws(IOException::class)
  fun deleteRootRecord(storage: Path, rootId: Int)

  @Throws(IOException::class)
  fun loadRootData(storage: Path, id: Int, path: String, fs: NewVirtualFileSystem) {
  }

  @Throws(IOException::class)
  fun loadDirectoryData(storage: Path, id: Int, path: String, fs: NewVirtualFileSystem) {
  }
}