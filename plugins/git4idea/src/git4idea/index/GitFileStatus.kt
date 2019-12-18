// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

data class GitFileStatus(val index: StatusCode,
                         val workTree: StatusCode,
                         val path: FilePath,
                         val origPath: FilePath? = null) {

  constructor(root: VirtualFile, record: LightFileStatus.StatusRecord) :
    this(record.index, record.workTree, VcsUtil.getFilePath(root, record.path), record.origPath?.let { VcsUtil.getFilePath(root, it) })
}