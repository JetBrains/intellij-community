// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.animation

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.function.Consumer

@ApiStatus.Internal
class AlphaAnimationContext(private val base: AlphaComposite, private val consumer: Consumer<AlphaComposite?>) {
  constructor(consumer: Consumer<AlphaComposite?>) : this(AlphaComposite.SrcOver, consumer)
  constructor(component: Component) : this({ if (component.isShowing) component.repaint() }) {
    this.component = component
  }

  var component: Component? = null

  var composite: AlphaComposite? = null
    private set

  val animator: ShowHideAnimator = ShowHideAnimator {
    composite = when {
      it <= 0.0 -> null
      it >= 1.0 -> base
      else -> base.derive(it.toFloat())
    }
    consumer.accept(composite)
  }

  var isVisible: Boolean
    get() = composite != null
    set(visible) {
      animator.setVisible(visible) {
        component?.isVisible = visible
      }
    }

  val disposable: Disposable get() = animator.disposable

  fun paint(g: Graphics, paint: Runnable) {
    when {
      g is Graphics2D -> paintWithComposite(g, paint)
      composite != null -> paint.run()
    }
  }

  fun paintWithComposite(g: Graphics2D, paint: Runnable) {
    composite?.let {
      val old = g.composite
      try {
        g.composite = it
        paint.run()
      }
      finally {
        g.composite = old
      }
    }
  }
}
