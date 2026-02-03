// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FileUtils")
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.IndexedFileImpl

fun findOriginalFile(file: VirtualFile?): VirtualFile? =
  file?.let {
    var f: VirtualFile? = it
    while (f is LightVirtualFileBase) {
      f = (f as? VirtualFileWindow)?.delegate ?: f.originalFile
    }
    f
  }

@JvmOverloads
fun isFileIndexed(project: Project, file: VirtualFile, indexId: ID<*, *> = StubUpdatingIndex.INDEX_ID): Boolean {
  if (file !is VirtualFileWithId) return false

  return (FileBasedIndex.getInstance() as FileBasedIndexEx)
    .getIndex(indexId)
    .getIndexingStateForFile(file.id, IndexedFileImpl(file, project))
    .isNotIndexed.not()
}