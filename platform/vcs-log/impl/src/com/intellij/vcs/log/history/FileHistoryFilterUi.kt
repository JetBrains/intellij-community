// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogFilterUi
import com.intellij.vcs.log.history.FileHistoryFilterer.Companion.createFilters

class FileHistoryFilterUi(private val path: FilePath, private val hash: Hash?, private val root: VirtualFile,
                          private val properties: FileHistoryUiProperties) : VcsLogFilterUi {
  override fun getFilters(): VcsLogFilterCollection {
    return createFilters(path, hash, root, properties.get(FileHistoryUiProperties.SHOW_ALL_BRANCHES))
  }
}