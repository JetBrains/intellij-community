// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.wsl.AbstractWslDistribution


/**
 * Linux or Windows file storage
 * [MyFileType] is file type for this storage (Linux uses [LinuxFileStorage] and Windows is [WindowsFileStorage])
 * [OtherSideFileType] is for other side.
 * [FilePathRelativeToDir] are relative to [dir]
 */
abstract class FileStorage<MyFileType, OtherSideFileType>(
  protected val dir: MyFileType,
  protected val distro: AbstractWslDistribution,
  protected val onlyExtensions: Array<String>
) {

  /**
   * Create links in `source->target` format
   */
  abstract fun createSymLinks(links: Map<FilePathRelativeToDir, FilePathRelativeToDir>)

  /**
   * List of [WslHashRecord] (file + hash) and map of `source->target` links.
   * [skipHashCalculation] saves time by skipping hash (hence [WslHashRecord.hash] is 0).
   * Such records can't be used for sync, but only to copy all files
   */
  abstract fun getHashesAndLinks(skipHashCalculation: Boolean): Pair<List<WslHashRecord>, Map<FilePathRelativeToDir, FilePathRelativeToDir>>

  /**
   * is [dir] empty
   */
  abstract fun isEmpty(): Boolean
  abstract fun removeFiles(filesToRemove: Collection<FilePathRelativeToDir>)
  abstract fun createTempFile(): MyFileType
  abstract fun removeTempFile(file: MyFileType)

  /**
   * tar [files] and copy to [destTar]
   */
  abstract fun tarAndCopyTo(files: Collection<FilePathRelativeToDir>, destTar: OtherSideFileType)
  abstract fun unTar(tarFile: MyFileType)
  abstract fun removeLinks(vararg linksToRemove: FilePathRelativeToDir)
}