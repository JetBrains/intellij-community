// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ui.CommitLegendPanel.InfoCalculator
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.math.max
import kotlin.properties.Delegates.observable

@Deprecated(replaceWith = ReplaceWith("CommitLegendComponent"), message = "Use CommitLegendComponent.create")
@ApiStatus.ScheduledForRemoval
open class CommitLegendPanel(private val myInfoCalculator: InfoCalculator) {
  private val myRootPanel = SimpleColoredComponent().apply { isOpaque = false  }
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

class CommitLegendComponent private constructor(private val orderedLegendChunks: List<LegendChunk>) {
  val component: SimpleColoredComponent = SimpleColoredComponent().apply { isOpaque = false }

  var isCompact: Boolean by observable(false) { _, oldValue, newValue ->
    if (oldValue != newValue) update()
  }

  fun update() {
    component.clear()
    orderedLegendChunks.forEach {
      it.appendTo(component, isCompact)
    }
    component.accessibleContext.accessibleName = getFullLegendString()
    component.isVisible = !component.isEmpty()
  }

  fun getFullLegendString(): @NlsSafe String = orderedLegendChunks
    .map { it.formatWithLabel(false) }
    .filterNot { it.isEmpty() }
    .joinToString(SEPARATOR)

  companion object {
    @JvmStatic
    fun defaultLegendChunks(infoCalculator: InfoCalculator): List<LegendChunk> = listOf(
      LegendChunk.Added(infoCalculator),
      LegendChunk.Modified(infoCalculator),
      LegendChunk.Deleted(infoCalculator),
    )

    @JvmStatic
    @JvmOverloads
    fun create(
      infoCalculator: InfoCalculator,
      legendChunks: List<LegendChunk> = defaultLegendChunks(infoCalculator),
    ): CommitLegendComponent = CommitLegendComponent(legendChunks)
  }
}

abstract class LegendChunk {
  abstract fun formatNumbers(): @Nls String
  abstract val fullLabel: @Nls String
  abstract val compactLabel: @NlsSafe String
  abstract val fileStatus: FileStatus

  internal class Added(
    private val calculator: InfoCalculator,
    override val compactLabel: @NlsSafe String = "+",
    override val fileStatus: FileStatus = FileStatus.ADDED,
  ) : LegendChunk() {
    override val fullLabel: @Nls String get() = message("commit.legend.new")

    override fun formatNumbers(): @Nls String {
      val new = calculator.includedNew
      val unversioned = calculator.includedUnversioned

      return when {
        new > 0 && unversioned > 0 -> "${new.formatInt()}+${unversioned.formatInt()}"
        new > 0 -> new.formatInt()
        unversioned > 0 -> unversioned.formatInt()
        else -> ""
      }
    }
  }

  internal class Modified(
    private val calculator: InfoCalculator,
    override val compactLabel: @NlsSafe String = "*",
    override val fileStatus: FileStatus = FileStatus.MODIFIED,
  ) : LegendChunk() {
    override val fullLabel: @Nls String get() = message("commit.legend.modified")
    override fun formatNumbers(): @Nls String = LegendChunkFormatters.nonZeroOrEmpty(calculator.includedModified)
  }

  internal class Deleted(
    private val calculator: InfoCalculator,
    override val compactLabel: @NlsSafe String = "-",
    override val fileStatus: FileStatus = FileStatus.DELETED,
  ) : LegendChunk() {
    override val fullLabel: @Nls String get() = message("commit.legend.deleted")
    override fun formatNumbers(): @Nls String = LegendChunkFormatters.nonZeroOrEmpty(calculator.includedDeleted)
  }
}

private fun LegendChunk.formatWithLabel(isCompact: Boolean): @Nls String {
  val value = formatNumbers()
  if (value.isBlank()) return ""

  if (isCompact) return "$compactLabel$value"

  return "$value $fullLabel"
}

private fun LegendChunk.appendTo(coloredComponent: SimpleColoredComponent, isCompact: Boolean) {
  val formatWithLabel = formatWithLabel(isCompact)
  if (formatWithLabel.isBlank()) return

  if (!coloredComponent.isEmpty()) {
    coloredComponent.append(SEPARATOR)
  }
  coloredComponent.append(formatWithLabel, fileStatus.attributes)
}

private fun SimpleColoredComponent.isEmpty() = !iterator().hasNext()
private const val SEPARATOR: @Nls String = "   "

private val FileStatus.attributes
  get() = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.lazy { color ?: UIUtil.getLabelForeground() })

internal object LegendChunkFormatters {
  @JvmStatic
  fun nonZeroOrEmpty(int: Int): @Nls String = if (int > 0) int.formatInt() else ""
}

private fun Int.formatInt(): @Nls String = "%,d".format(this) // NON-NLS