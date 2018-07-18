// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import kotlin.math.max

private val FileStatus.attributes get() = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color)

open class CommitLegendPanel(private val myInfoCalculator: InfoCalculator) {
  private val myRootPanel = SimpleColoredComponent()
  private val isPanelEmpty get() = !myRootPanel.iterator().hasNext()

  val component get() = myRootPanel

  open fun update() {
    myRootPanel.clear()
    appendLegend()
  }

  private fun appendLegend() = with(myInfoCalculator) {
    appendAdded(includedNew, includedUnversioned)
    append(includedModified, FileStatus.MODIFIED, message("commit.legend.modified"))
    append(includedDeleted, FileStatus.DELETED, message("commit.legend.deleted"))
  }

  protected fun append(included: Int, fileStatus: FileStatus, labelName: String) {
    if (included > 0) {
      if (!isPanelEmpty) {
        appendSpace()
      }
      myRootPanel.append("$included $labelName", fileStatus.attributes)
    }
  }

  private fun appendAdded(new: Int, unversioned: Int) {
    if (new > 0 || unversioned > 0) {
      if (!isPanelEmpty) {
        appendSpace()
      }
      val labelName = message("commit.legend.new")
      val text = if (new > 0 && unversioned > 0) "$new+$unversioned $labelName" else "${max(new, unversioned)} $labelName"
      myRootPanel.append(text, FileStatus.ADDED.attributes)
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
