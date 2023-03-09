// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface VcsLogFileHistoryHandler {

  @Throws(VcsException::class)
  fun getHistoryFast(root: VirtualFile, filePath: FilePath, hash: Hash?, commitCount: Int): List<VcsFileRevisionEx>

  @Throws(VcsException::class)
  fun getRename(root: VirtualFile, filePath: FilePath, beforeHash: Hash, afterHash: Hash): Rename?

  data class Rename(val filePath1: FilePath, val filePath2: FilePath, val hash1: Hash, val hash2: Hash)
}