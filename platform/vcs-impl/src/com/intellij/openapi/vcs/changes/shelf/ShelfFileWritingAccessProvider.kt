// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider

class ShelfFileWritingAccessProvider(val myProject: Project,
                                     private val myShelveChangeManger: ShelveChangesManager) : WritingAccessProvider() {
  override fun requestWriting(files: MutableCollection<out VirtualFile>): MutableCollection<VirtualFile> {
    return files.filter { myShelveChangeManger.shelvingFiles.contains(it) }.toMutableList()
  }

  override fun getReadOnlyMessage(): String {
    return "The file is locked for editing while being shelved";
  }
}
