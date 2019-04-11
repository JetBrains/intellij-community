// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.FilePath
import java.util.*

/**
 * not a bug: Orders directories, shortest paths first, files are left as is.
 * correct alphabet ordering is not here
 */
class FilePathHierarchicalComparator : Comparator<FilePath> {

  override fun compare(p1: FilePath, p2: FilePath): Int {
    val isDir1 = p1.isDirectory
    val isDir2 = p2.isDirectory

    if (!isDir1 && !isDir2) return 0
    if (isDir1 && !isDir2) return -1
    return if (!isDir1) 1 else Integer.compare(p1.path.length, p2.path.length)

  }
}

@JvmField
val FILE_PATH_HIERARCHICAL_COMPARATOR = FilePathHierarchicalComparator()
