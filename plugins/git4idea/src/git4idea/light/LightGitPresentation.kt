// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.util.PathUtil
import com.intellij.util.ui.UIUtil
import git4idea.i18n.GitBundle
import git4idea.index.LightFileStatus
import git4idea.index.StatusCode
import git4idea.index.getFileStatus
import org.jetbrains.annotations.Nls
import java.awt.Color

val LightFileStatus.color: Color?
  get() = getFileStatus().color

@Nls
fun LightFileStatus.getPresentation(): String {
  return when (this) {
    LightFileStatus.Blank, is LightFileStatus.NotChanged -> ""
    is LightFileStatus.StatusRecord -> getPresentation()
  }
}

@Nls
private fun LightFileStatus.StatusRecord.getPresentation(): String {
  val fileName = PathUtil.getFileName(path)
  if (index == '!' || workTree == '!' || index == '?' || workTree == '?') return "$fileName: ${getPresentation(index)}"
  if (isConflicted()) {
    val status = if (index == workTree) {
      GitBundle.message("git.status.unmerged.both", getFileStatus(if (index == 'U') 'M' else index)!!.text.toLowerCase())
    }
    else {
      val indexPresentation = if (index == 'U') "" else GitBundle.message("git.status.unmerged.index", getPresentation(index).toLowerCase())
      val workTreePresentation = if (workTree == 'U') "" else GitBundle.message("git.status.unmerged.work.tree",
                                                                                getPresentation(workTree).toLowerCase())
      when {
        indexPresentation.isBlank() -> workTreePresentation
        workTreePresentation.isBlank() -> indexPresentation
        else -> "$indexPresentation, $workTreePresentation"
      }
    }
    return "$fileName: ${getPresentation('U')} ($status)"
  }
  val indexPresentation = if (index == ' ') "" else GitBundle.message("git.status.index", getPresentation(index))
  val workTreePresentation = if (workTree == ' ') "" else GitBundle.message("git.status.work.tree", getPresentation(workTree))
  if (indexPresentation.isBlank()) return "$fileName: $workTreePresentation"
  if (workTreePresentation.isBlank()) return "$fileName: $indexPresentation"
  return "$fileName:${UIUtil.BR}$indexPresentation${UIUtil.BR}$workTreePresentation"
}

private fun getPresentation(status: StatusCode): @Nls String {
  return when (status) {
    ' ' -> GitBundle.message("git.status.not.changed")
    'R' -> GitBundle.message("git.status.renamed")
    'C' -> GitBundle.message("git.status.copied")
    'T' -> GitBundle.message("git.status.type.changed")
    'U' -> GitBundle.message("git.status.unmerged")
    '?' -> GitBundle.message("git.status.untracked")
    else -> getFileStatus(status)!!.text
  }
}