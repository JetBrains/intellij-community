// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView

import com.intellij.openapi.vfs.VirtualFile

interface FileProblem : Problem {
  /**
   * The file that the problem belongs to.
   */
  val file: VirtualFile

  /**
   * Zero-based line number in the corresponding file,
   * or -1 if there is no problem line to navigate.
   */
  val line: Int
    get() = -1

  /**
   * Zero-based column number in the corresponding file.
   */
  val column: Int
    get() = -1
}
