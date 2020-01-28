// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath

internal class DirtBuilder() {
  private val fileTypeManager = FileTypeManager.getInstance()

  val filesForVcs = VcsDirtyScopeMap()
  val dirsForVcs = VcsDirtyScopeMap()
  var isEverythingDirty: Boolean = false

  constructor(builder: DirtBuilder) : this() {
    filesForVcs.addAll(builder.filesForVcs)
    dirsForVcs.addAll(builder.dirsForVcs)
    isEverythingDirty = builder.isEverythingDirty
  }

  fun isEmpty(): Boolean = filesForVcs.isEmpty() && dirsForVcs.isEmpty()

  fun reset() {
    filesForVcs.clear()
    dirsForVcs.clear()
    isEverythingDirty = false
  }

  fun addDirtyFile(vcs: AbstractVcs, file: FilePath) {
    if (fileTypeManager.isFileIgnored(file.name)) return
    filesForVcs.add(vcs, file)
  }

  fun addDirtyDirRecursively(vcs: AbstractVcs, dir: FilePath) {
    if (fileTypeManager.isFileIgnored(dir.name)) return
    dirsForVcs.add(vcs, dir)
  }
}