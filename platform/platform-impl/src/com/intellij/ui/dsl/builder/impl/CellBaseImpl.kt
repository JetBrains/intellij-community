// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.layout.ComponentPredicate
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal sealed class CellBaseImpl<T : CellBase<T>> : CellBase<T> {

  var horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT
    private set

  var verticalAlign: VerticalAlign = VerticalAlign.CENTER
    private set

  var resizableColumn: Boolean = false
    private set

  var rightGap: RightGap? = null
    private set

  var customGaps: UnscaledGaps? = null

  abstract fun visibleFromParent(parentVisible: Boolean)

  abstract fun enabledFromParent(parentEnabled: Boolean)

  override fun visibleIf(predicate: ComponentPredicate): CellBase<T> {
    visible(predicate())
    predicate.addListener { visible(it) }
    return this
  }

  override fun visibleIf(property: ObservableProperty<Boolean>): CellBase<T> {
    return visibleIf(ComponentPredicate.fromObservableProperty(property))
  }

  override fun enabledIf(predicate: ComponentPredicate): CellBase<T> {
    enabled(predicate())
    predicate.addListener { enabled(it) }
    return this
  }

  override fun enabledIf(property: ObservableProperty<Boolean>): CellBase<T> {
    return enabledIf(ComponentPredicate.fromObservableProperty(property))
  }

  @Deprecated("Use align(AlignX.LEFT/CENTER/RIGHT/FILL) method instead")
  @ApiStatus.ScheduledForRemoval
  override fun horizontalAlign(horizontalAlign: HorizontalAlign): CellBase<T> {
    this.horizontalAlign = horizontalAlign
    return this
  }

  @Deprecated("Use align(AlignY.TOP/CENTER/BOTTOM/FILL) method instead")
  @ApiStatus.ScheduledForRemoval
  override fun verticalAlign(verticalAlign: VerticalAlign): CellBase<T> {
    this.verticalAlign = verticalAlign
    return this
  }

  override fun align(align: Align): CellBase<T> {
    when (align) {
      is AlignX -> setAlign(align, null)
      is AlignY -> setAlign(null, align)
      is AlignBoth -> {
        setAlign(align.alignX, align.alignY)
      }
    }
    return this
  }

  override fun resizableColumn(): CellBase<T> {
    this.resizableColumn = true
    return this
  }

  override fun gap(rightGap: RightGap): CellBase<T> {
    this.rightGap = rightGap
    return this
  }

  @Deprecated("Use customize(UnscaledGaps) instead")
  @ApiStatus.ScheduledForRemoval
  override fun customize(customGaps: Gaps): CellBase<T> {
    return customize(customGaps.toUnscaled())
  }

  override fun customize(customGaps: UnscaledGaps): CellBase<T> {
    this.customGaps = customGaps
    return this
  }

  private fun setAlign(alignX: AlignX?, alignY: AlignY?) {
    alignX?.let {
      horizontalAlign = when (it) {
        AlignX.LEFT -> HorizontalAlign.LEFT
        AlignX.CENTER -> HorizontalAlign.CENTER
        AlignX.RIGHT -> HorizontalAlign.RIGHT
        AlignX.FILL -> HorizontalAlign.FILL
      }
    }

    alignY?.let {
      verticalAlign = when (it) {
        AlignY.TOP -> VerticalAlign.TOP
        AlignY.CENTER -> VerticalAlign.CENTER
        AlignY.BOTTOM -> VerticalAlign.BOTTOM
        AlignY.FILL -> VerticalAlign.FILL
      }
    }
  }
}
