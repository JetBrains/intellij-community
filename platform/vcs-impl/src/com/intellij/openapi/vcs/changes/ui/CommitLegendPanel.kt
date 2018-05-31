// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes

open class CommitLegendPanel(private val myInfoCalculator: InfoCalculator) {
  private val myRootPanel = SimpleColoredComponent()
  private val isPanelEmpty get() = !myRootPanel.iterator().hasNext()

  val component get() = myRootPanel

  open fun update() {
    myRootPanel.clear()
    appendText(myInfoCalculator.new, myInfoCalculator.includedNew, FileStatus.ADDED, VcsBundle.message("commit.legend.new"))
    appendText(myInfoCalculator.modified, myInfoCalculator.includedModified, FileStatus.MODIFIED,
               VcsBundle.message("commit.legend.modified"))
    appendText(myInfoCalculator.deleted, myInfoCalculator.includedDeleted, FileStatus.DELETED, VcsBundle.message("commit.legend.deleted"))
    appendText(myInfoCalculator.unversioned, myInfoCalculator.includedUnversioned, FileStatus.UNKNOWN,
               VcsBundle.message("commit.legend.unversioned"))
  }

  protected fun appendText(total: Int, included: Int, fileStatus: FileStatus, labelName: String) {
    if (total > 0) {
      if (!isPanelEmpty) {
        appendSpace()
      }
      val pattern = if (total == included) "%s %d" else "%s %d of %d"
      val text = String.format(pattern, labelName, included, total)
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
