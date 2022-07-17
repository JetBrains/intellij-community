// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import kotlin.math.max
import kotlin.properties.Delegates.observable

private val FileStatus.attributes
  get() = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.lazy { color ?: UIUtil.getLabelForeground() })

private fun Int.formatInt(): String = "%,d".format(this) // NON-NLS

open class CommitLegendPanel(private val myInfoCalculator: InfoCalculator) {
  private val myRootPanel = SimpleColoredComponent()
  private val isPanelEmpty get() = !myRootPanel.iterator().hasNext()

  val component get() = myRootPanel

  var isCompact: Boolean by observable(false) { _, oldValue, newValue ->
    if (oldValue != newValue) update()
  }

  open fun update() {
    myRootPanel.clear()
    appendLegend()
    myRootPanel.isVisible = !isPanelEmpty
  }

  private fun appendLegend() = with(myInfoCalculator) {
    appendAdded(includedNew, includedUnversioned)
    append(includedModified, FileStatus.MODIFIED, message("commit.legend.modified"), "*")
    append(includedDeleted, FileStatus.DELETED, message("commit.legend.deleted"), "-")
  }

  @JvmOverloads
  protected fun append(included: Int, fileStatus: FileStatus, @Nls label: String, compactLabel: @Nls String? = null) {
    if (included > 0) {
      if (!isPanelEmpty) {
        appendSpace()
      }
      myRootPanel.append(format(included.formatInt(), label, compactLabel), fileStatus.attributes)
    }
  }

  private fun appendAdded(new: Int, unversioned: Int) {
    if (new > 0 || unversioned > 0) {
      if (!isPanelEmpty) {
        appendSpace()
      }
      val value = if (new > 0 && unversioned > 0) "${new.formatInt()}+${unversioned.formatInt()}" else max(new, unversioned).formatInt()
      myRootPanel.append(format(value, message("commit.legend.new"), "+"), FileStatus.ADDED.attributes)
    }
  }

  protected fun appendSpace() {
    myRootPanel.append("   ")
  }

  @Nls
  private fun format(value: Any, @Nls label: String, compactLabel: @Nls String?): String =
    if (isCompact && compactLabel != null) "$compactLabel$value" else "$value $label"

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
