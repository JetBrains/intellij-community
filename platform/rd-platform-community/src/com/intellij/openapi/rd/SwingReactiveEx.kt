// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd

import com.intellij.openapi.wm.IdeGlassPane
import com.jetbrains.rd.swing.awtMousePoint
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.reactive.map
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent
import javax.swing.SwingUtilities

fun IdeGlassPane.mouseMoved(): ISource<MouseEvent> {
  return object : ISource<MouseEvent> {
    override fun advise(lifetime: Lifetime, handler: (MouseEvent) -> Unit) {
      val listener = object : MouseMotionAdapter() {
        override fun mouseMoved(e: MouseEvent?) {
          if (e != null) {
            handler(e)
          }
        }
      }

      this@mouseMoved.addMouseMotionPreprocessor(listener, lifetime.createNestedDisposable())
    }
  }
}

fun IdeGlassPane.childAtMouse(container: Container): ISource<Component?> = this@childAtMouse.mouseMoved()
  .map { SwingUtilities.convertPoint(it.component, it.x, it.y, container) }
  .map { container.getComponentAt(it) }

fun JComponent.childAtMouse(): IPropertyView<Component?> = this@childAtMouse.awtMousePoint()
  .map {
    if (it == null) null
    else {
      this@childAtMouse.getComponentAt(it)
    }
  }