// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff

import com.intellij.diff.vcs.DiffVcsFacade
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

class DiffVcsFacadeImpl : DiffVcsFacade() {
  override fun getFilePath(virtualFile: VirtualFile): FilePath {
    return VcsUtil.getFilePath(virtualFile)
  }
}