// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShelfFileWritingAccessProvider(val myProject: Project) : WritingAccessProvider() {
  override fun requestWriting(files: MutableCollection<out VirtualFile>): MutableCollection<VirtualFile> {
    val shelvingFiles = ShelveChangesManager.getInstance(myProject).shelvingFiles
    return files.intersect(shelvingFiles).toMutableSet()
  }

  override fun getReadOnlyMessage(): String {
    return VcsBundle.message("shelve.file.is.locked.for.editing.message")
  }
}
