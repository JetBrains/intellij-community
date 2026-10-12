// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.BatchingFileSystem
import org.jetbrains.annotations.ApiStatus

/**
 * Implement if your [NewVirtualFileSystem] can tell an unavailable environment from a missing file.
 *
 * The methods have the contracts of [NewVirtualFileSystem.getAttributes] and [BatchingFileSystem.listWithAttributes], with one difference.
 * When the environment behind the path is not connected, they throw [FileSystemUnavailableException].
 * They do not answer "absent" or "empty" in that case.
 * The VFS refresh uses them, so it does not delete the files of an unavailable environment.
 */
@ApiStatus.Internal
interface UnavailabilityAwareFileSystem {
  /** @return the attributes of [file], or `null` if the file does not exist. */
  @Throws(FileSystemUnavailableException::class)
  fun getAttributesOrThrowIfUnavailable(file: VirtualFile): FileAttributes?

  /**
   * @param names has the meaning of `childrenNames` in [BatchingFileSystem.listWithAttributes].
   * @return the children of [dir] with their attributes.
   */
  @Throws(FileSystemUnavailableException::class)
  fun listWithAttributesOrThrowIfUnavailable(dir: VirtualFile, names: Set<String>?): Map<String, FileAttributes>
}
