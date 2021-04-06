// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.event.AncestorEvent
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.function.Consumer


@Experimental
abstract class ToolbarActionTracker: Disposable {
  private var pointProvider: ((Component) -> Point)? = null

  /**
   * pointProvider - point in toolbar coordinates
   */
  class ActionContext(val tracker: ToolbarActionTracker, val pointProvider: (Component) -> Point)

  inner class MyComponentAdapter : ComponentAdapter() {
    override fun componentMoved(event: ComponentEvent) {
      hideOrRepaint(event.component as JComponent)
    }

    override fun componentResized(event: ComponentEvent) {
      if (!wasCreated() && !event.component.bounds.isEmpty && event.component.isShowing && pointProvider != null) {
        init(event.component as JComponent, pointProvider!!)
      }
      else {
        hideOrRepaint(event.component as JComponent)
      }
    }
  }

  inner class MyAncestorAdapter(val component: JComponent) : AncestorListenerAdapter() {
    override fun ancestorRemoved(event: AncestorEvent) {
      hideOrRepaint(component)
      hidePopup()
    }

    override fun ancestorMoved(event: AncestorEvent?) {
      hideOrRepaint(component)
    }
  }
  private val componentAdapter = MyComponentAdapter()
  private var ancestorListener : MyAncestorAdapter? = null

  protected fun followToolbarComponent(component: JComponent, toolbar: JComponent, pointProvider: (Component) -> Point) {
    if (canShow()) {
      this.pointProvider = pointProvider
      component.addComponentListener(componentAdapter.also { Disposer.register(this, Disposable { component.removeComponentListener(it) }) })
      ancestorListener = MyAncestorAdapter(component)
      toolbar.addAncestorListener(ancestorListener.also{ Disposer.register(this, Disposable { component.removeAncestorListener(it) }) })
    }
  }

  protected fun unfollowComponent(component: JComponent){
    component.removeComponentListener(componentAdapter)
    component.removeAncestorListener(ancestorListener)
  }


  /**
   * Bind the tooltip to action's presentation. Then <code>ActionToolbar</code> starts following ActionButton with
   * the tooltip if it can be shown. Term "follow" is used because ActionToolbar updates its content and ActionButton's
   * showing status / location may change in time.
   */
  abstract fun assignTo(presentation: Presentation, pointProvider: (Component) -> Point, disposeAction: Runnable? = null)

  abstract fun wasCreated(): Boolean

  abstract fun hidePopup()

  abstract fun init(component: JComponent, pointProvider: (Component) -> Point)

  abstract fun createAndShow(component: JComponent, pointProvider: (Component) -> Point): Any

  abstract fun hideOrRepaint(component: JComponent)

  abstract fun canShow(): Boolean

  abstract fun show(component: JComponent, pointProvider: (Component) -> Point)

  companion object {
    const val PROPERTY_PREFIX = "toolbar.tracker"
    val PRESENTATION_GOT_IT_KEY = Key<ActionContext>("${PROPERTY_PREFIX}.gotit.presentation")
    val PRESENTATION_POPUP_KEY = Key<ActionContext>("${PROPERTY_PREFIX}.popup.presentation")

    @JvmField
    val ARROW_SHIFT = JBUIScale.scale(20) + Registry.intValue("ide.balloon.shadow.size") + BalloonImpl.ARC.get()
    /**
     * Use this method for following an ActionToolbar component.
     */
    @JvmStatic
    fun followToolbarComponent(presentation: Presentation, component: JComponent, toolbar: JComponent) {
      presentation.getClientProperty(PRESENTATION_GOT_IT_KEY)?.let {
        it.tracker.followToolbarComponent(component, toolbar, it.pointProvider)
      }
      presentation.getClientProperty(PRESENTATION_POPUP_KEY)?.let {
        it.tracker.followToolbarComponent(component, toolbar, it.pointProvider)
      }
    }
  }

}