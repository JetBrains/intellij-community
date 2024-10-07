// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.observable.util.whenMouseMoved
import com.intellij.openapi.ui.isComponentUnderMouse
import com.intellij.openapi.ui.isFocusAncestor
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle
import javax.swing.JComponent

class FloatingToolbar(
  private val ownerComponent: JComponent,
  actionGroup: ActionGroup,
  private val parentDisposable: Disposable,
) : AbstractFloatingToolbarComponent(actionGroup, parentDisposable) {

  override val autoHideable: Boolean = true

  private var boundsWithoutScrolling: Rectangle? = null

  override fun isComponentOnHold(): Boolean {
    return isComponentUnderMouse() || isFocusAncestor()
  }

  override fun installMouseMotionWatcher() {
    ownerComponent.whenMouseMoved(parentDisposable) {
      scheduleShow()
    }
  }

  @ApiStatus.Internal
  fun setBoundsWithScrollingDy(x: Int, y: Int, width: Int, height: Int, scrollingDy: Int) {
    boundsWithoutScrolling = bounds
    boundsWithoutScrolling = Rectangle(x, y, width, height)
    if (scrollingDy <= 0)
      bounds = boundsWithoutScrolling!!
    else
      setBounds(x, y - scrollingDy, width, height)
  }

  @ApiStatus.Internal
  fun setScrollingDy(scrollingDy: Int) {
    val bounds = boundsWithoutScrolling ?: bounds
    setBounds(bounds.x, bounds.y - scrollingDy, bounds.width, bounds.height)
  }

  @ApiStatus.Internal
  fun setX(x: Int) {
    val oldBounds = boundsWithoutScrolling ?: bounds
    setBoundsWithScrollingDy(
      x,
      oldBounds.getY().toInt(),
      oldBounds.getWidth().toInt(),
      oldBounds.getHeight().toInt(),
      oldBounds.getY().toInt() - bounds.getY().toInt()
    )
  }

  init {
    init(ownerComponent)
    showingTime = 150
    hidingTime = 50
    //backgroundAlpha = 1F
  }
}