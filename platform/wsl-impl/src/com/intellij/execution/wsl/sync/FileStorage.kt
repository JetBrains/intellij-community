// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.wsl.AbstractWslDistribution


/**
 * Linux or Windows file storage
 * [MyFileType] is file type for this storage (Linux uses [LinuxFileStorage] and Windows is [WindowsFileStorage])
 * [OtherSideFileType] is for other side.
 * [FilePathRelativeToDir] are relative to [dir]
 */
internal abstract class FileStorage<MyFileType, OtherSideFileType>(
  protected val dir: MyFileType,
  protected val distro: AbstractWslDistribution,
  protected val onlyExtensions: Array<String>
) {

  /**
   * File names relative to [dir] as strings
   */
  abstract fun getAllFilesInDir(): Collection<FilePathRelativeToDir>
  abstract fun getHashes(): List<WslHashRecord>

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
}