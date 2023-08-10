// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch.formove

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeImpl
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

internal class UndoApplyPatchDialog(
  private val project: Project,
  private val failedFilePaths: List<FilePath>,
  private val shouldInformAboutBinaries: Boolean
) : DialogWrapper(project, true) {

  init {
    title = VcsBundle.message("patch.apply.partly.failed.title")
    setOKButtonText(VcsBundle.message("patch.apply.rollback.action"))
    init()
  }

  override fun createCenterPanel(): JComponent {
    val numFiles = failedFilePaths.size
    return panel {
      row {
        label(VcsBundle.message("patch.apply.rollback.prompt", numFiles))
      }
      if (numFiles > 0) {
        val browser: ChangesTree = AsyncChangesTreeImpl.FilePaths(project, false, false, failedFilePaths)
        row {
          scrollCell(browser)
            .align(Align.FILL)
        }.resizableRow()
      }
      row {
        label(VcsBundle.message("patch.apply.rollback.prompt.bottom"))
      }
      if (shouldInformAboutBinaries) {
        row {
          label(VcsBundle.message("patch.apply.rollback.will.not.affect.binaries.info", numFiles)).applyToComponent {
            icon = UIUtil.getBalloonWarningIcon()
          }
        }
      }
    }
  }
}
