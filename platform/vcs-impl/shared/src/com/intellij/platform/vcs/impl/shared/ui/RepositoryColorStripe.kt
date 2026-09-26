// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.ui

import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics2D

@ApiStatus.Internal
enum class RepositoryColorStripeSegment { START, MIDDLE, END, SINGLE }

/**
 * One drawing algorithm for the per-repository color stripe used by both the VCS Log tab's root-color column and
 * the Git Worktrees tab's per-repository row color, so the two read as the same visual language rather than two
 * independently-tuned lookalikes. [LEFT_GAP]/[WIDTH]/[ARC]/[BOTTOM_GAP] are that shared design size, exposed so a
 * caller with unusual geometry (e.g. a resizable table column) can override the ones it must compute itself while
 * still deferring to the shared values for everything else.
 */
@ApiStatus.Internal
object RepositoryColorStripe {
  val LEFT_GAP: Int get() = JBUI.scale(2)
  val WIDTH: Int get() = JBUI.scale(4)
  val ARC: Int get() = JBUI.scale(4)
  val BOTTOM_GAP: Int get() = JBUI.scale(2)

  fun resolveSegment(samePrev: Boolean, sameNext: Boolean): RepositoryColorStripeSegment = when {
    samePrev && sameNext -> RepositoryColorStripeSegment.MIDDLE
    !samePrev && !sameNext -> RepositoryColorStripeSegment.SINGLE
    samePrev -> RepositoryColorStripeSegment.END
    else -> RepositoryColorStripeSegment.START
  }

  fun paintSegment(
    g: Graphics2D,
    color: Color,
    height: Int,
    segment: RepositoryColorStripeSegment,
    width: Int = WIDTH,
    x: Int = LEFT_GAP,
    arc: Int = ARC,
    bottomGap: Int = BOTTOM_GAP,
  ) {
    val config = GraphicsUtil.setupAAPainting(g)
    g.color = color
    when (segment) {
      RepositoryColorStripeSegment.START -> g.fillRoundRect(x, 0, width, height + arc, arc, arc)
      RepositoryColorStripeSegment.MIDDLE -> g.fillRect(x, 0, width, height)
      RepositoryColorStripeSegment.END -> g.fillRoundRect(x, -arc, width, height + bottomGap, arc, arc)
      RepositoryColorStripeSegment.SINGLE -> g.fillRoundRect(x, 0, width, height - bottomGap, arc, arc)
    }
    config.restore()
  }
}
