// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editorTabGrouping.impl

import com.intellij.openapi.Disposable
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.animation.Animation
import com.intellij.util.animation.Easing
import com.intellij.util.animation.JBAnimator
import java.awt.Dimension
import java.util.function.DoubleConsumer
import kotlin.math.roundToInt
import org.jetbrains.annotations.TestOnly

private const val DURATION_MS = 150

/**
 * Animates the width of a tab group that collapses or expands, so that the other tabs slide to their new position.
 *
 * The tab layout takes the width of each tab from the preferred size of its label. During an animation the labels
 * of the group have a set preferred width. The animation removes it at its end.
 * The animator shows the final state at once in the power save mode and in a remote session.
 */
internal class TabGroupCollapseAnimation(private val tabs: JBTabsImpl, parentDisposable: Disposable) {
  private val animator = JBAnimator(parentDisposable)
  private val changingGroups = ArrayList<ChangingGroup>()
  private var runs: List<GroupRun> = emptyList()

  val isRunning: Boolean get() = runs.isNotEmpty()

  /**
   * Records the width that the group has before it collapses into the [groupTab] or expands from it.
   * Only a horizontal tab row has an animation.
   */
  fun groupChanging(groupTab: TabInfo, members: List<TabInfo>, collapsing: Boolean) {
    if (!tabs.isHorizontalTabs) return
    val startWidth = shownWidth(if (collapsing) members else listOf(groupTab))
    if (startWidth > 0) changingGroups.add(ChangingGroup(if (collapsing) listOf(groupTab) else members.toList(), startWidth))
  }

  /** Starts the animation of each recorded group. The labels must have their final content. */
  fun start() {
    if (changingGroups.isEmpty()) return
    // The selection of a group tab expands its group and then collapses the other group, in two changes.
    // So a running group continues from the width that it has now, unless it changes again.
    val changingInfos = changingGroups.flatMap { it.infos }
    val continuedGroups = runs.filter { run -> run.infos.none { it in changingInfos } }
      .map { ChangingGroup(it.infos, shownWidth(it.infos)) }
    for (run in runs) run.restoreOwnWidths()
    runs = (changingGroups + continuedGroups).mapNotNull(::createRun)
    changingGroups.clear()
    if (runs.isEmpty()) return
    showFrame(0.0)
    val animation = Animation(DoubleConsumer(::showFrame))
      .setDuration(DURATION_MS)
      .setEasing(Easing.EASE_OUT)
      .runWhenExpired(::finish)
    animator.animate(animation)
  }

  /** Ends the animation and gives each label its own width back. */
  @TestOnly
  fun finishNow() = finish()

  private fun shownWidth(infos: List<TabInfo>): Int = infos.sumOf { info -> if (info.isHidden) 0 else info.tabLabel?.width ?: 0 }

  /** Returns the run of [group], or null when the width of the group does not change. */
  private fun createRun(group: ChangingGroup): GroupRun? {
    val labels = group.infos.filterNot { it.isHidden }.mapNotNull { it.tabLabel }
    val ownSizes = labels.map { it.preferredSize }
    val ownWidth = ownSizes.sumOf { it.width }
    if (ownWidth <= 0 || ownWidth == group.startWidth) return null
    return GroupRun(group.infos, labels, ownSizes, group.startWidth)
  }

  private fun showFrame(fraction: Double) {
    if (runs.isEmpty()) return
    for (run in runs) run.setWidths(fraction)
    tabs.revalidateAndRepaint()
  }

  private fun finish() {
    if (runs.isEmpty()) return
    for (run in runs) run.restoreOwnWidths()
    runs = emptyList()
    tabs.revalidateAndRepaint()
  }
}

/** The tabs that a group shows after its change, and the width that the group had before it. */
private class ChangingGroup(val infos: List<TabInfo>, val startWidth: Int)

/** The [labels] of one group, which go from the [startWidth] to the sum of their own widths. */
private class GroupRun(
  val infos: List<TabInfo>,
  private val labels: List<TabLabel>,
  private val ownSizes: List<Dimension>,
  private val startWidth: Int,
) {
  private val ownWidth = ownSizes.sumOf { it.width }

  /** Gives the labels the width of the group at [fraction] of the animation. Each label keeps its share of the width. */
  fun setWidths(fraction: Double) {
    val groupWidth = (startWidth + (ownWidth - startWidth) * fraction).roundToInt()
    var usedWidth = 0
    for ((index, label) in labels.withIndex()) {
      val ownSize = ownSizes[index]
      // The last label takes the rest, so the rounding does not change the width of the group.
      val width = if (index == labels.lastIndex) groupWidth - usedWidth else ownSize.width * groupWidth / ownWidth
      usedWidth += width
      label.preferredSize = Dimension(width.coerceAtLeast(0), ownSize.height)
      label.revalidate()
    }
  }

  fun restoreOwnWidths() {
    for (label in labels) {
      // The label declares a non-null getter, so the property syntax does not take null.
      label.setPreferredSize(null)
      label.revalidate()
    }
  }
}
