// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.async

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.EdtScheduler
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
class AsyncPopupWaiter(step: AsyncPopupStep<*>, point: RelativePoint, onReady: (PopupStep<*>) -> Unit) : Disposable {
  private val myGlassPane = UIUtil.getRootPane(point.component)?.glassPane as? JComponent
  private val myIcon: JComponent?
  private var myDisposed = false

  init {
    myIcon = createLoadingIcon(point)

    step.promise.onSuccess {
      if (myDisposed) return@onSuccess
      onReady(it)
      Disposer.dispose(this)
    }
    step.promise.onError {
      Disposer.dispose(this)
    }
  }

  private fun createLoadingIcon(point: RelativePoint): JComponent? {
    if (myGlassPane == null) return null

    val icon = JLabel(AnimatedIcon.Default.INSTANCE)
    val size = icon.preferredSize
    icon.size = size

    val location = point.getPoint(myGlassPane)
    location.x -= size.width * 3
    location.y -= size.height / 2
    icon.location = location

    EdtScheduler.getInstance().schedule(Registry.intValue("actionSystem.popup.progress.icon.delay", 500)) {
      if (icon.isVisible && !myDisposed) {
        myGlassPane.add(icon)
      }
    }
    return icon
  }

  override fun dispose() {
    if (myDisposed) return

    myDisposed = true
    if (myGlassPane != null && myIcon != null) {
      myGlassPane.remove(myIcon)
    }
  }
}