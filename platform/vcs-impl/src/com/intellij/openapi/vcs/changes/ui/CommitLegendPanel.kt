// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes

open class CommitLegendPanel(private val myInfoCalculator: InfoCalculator) {
  private val myRootPanel = SimpleColoredComponent()
  private val isPanelEmpty get() = !myRootPanel.iterator().hasNext()

  val component get() = myRootPanel

  open fun update() {
    myRootPanel.clear()
    appendLegend()
  }

  private fun appendLegend() = with(myInfoCalculator) {
    append(new, includedNew, FileStatus.ADDED, message("commit.legend.new"))
    append(modified, includedModified, FileStatus.MODIFIED, message("commit.legend.modified"))
    append(deleted, includedDeleted, FileStatus.DELETED, message("commit.legend.deleted"))
    append(unversioned, includedUnversioned, FileStatus.UNKNOWN, message("commit.legend.unversioned"))
  }

  protected fun append(total: Int, included: Int, fileStatus: FileStatus, labelName: String) {
    if (total > 0) {
      if (!isPanelEmpty) {
        appendSpace()
      }
      val text = if (total == included) "$labelName $included" else "$labelName $included of $total"
      myRootPanel.append(text, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fileStatus.color))
    }
  }

  protected fun appendSpace() {
    myRootPanel.append("   ")
  }

  interface InfoCalculator {
    val new: Int
    val modified: Int
    val deleted: Int
    val unversioned: Int
    val includedNew: Int
    val includedModified: Int
    val includedDeleted: Int
    val includedUnversioned: Int
  }
}
