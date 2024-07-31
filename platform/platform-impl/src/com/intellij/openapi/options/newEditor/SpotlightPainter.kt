// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.options.newEditor

import com.intellij.ide.ui.search.ComponentHighlightingListener
import com.intellij.ide.ui.search.SearchUtil.lightOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.GlassPanel
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.showingScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics2D
import java.util.HashMap
import javax.swing.CellRendererPane
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

private val DO_NOT_SCROLL = Key.create<Boolean?>("SpotlightPainter.DO_NOT_SCROLL")

@OptIn(FlowPreview::class)
@ApiStatus.Internal
open class SpotlightPainter(
  private val target: JComponent,
  private val updater: (SpotlightPainter) -> Unit,
) : AbstractPainter() {
  private val configurableOption = HashMap<String?, String?>()
  private val glassPanel = GlassPanel(target)
  private var isVisible: Boolean = false

  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    val activatable = IdeGlassPaneUtil.createPainterActivatable(target, this)
    target.showingScope("SpotlightPainter") {
      activatable.showNotify()
      try {
        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(ComponentHighlightingListener.TOPIC, object : ComponentHighlightingListener {
          override fun highlight(component: JComponent, searchString: String) {
            // If several spotlight painters exist, they will receive each other updates,
            // because they share one message bus (ComponentHighlightingListener.TOPIC).
            // The painter should only draw spotlights for components in the hierarchy of `myTarget`
            if (UIUtil.isAncestor(target, component)) {
              glassPanel.addSpotlight(component)
              if (target.getClientProperty(DO_NOT_SCROLL) != true && center(component)) {
                target.putClientProperty(DO_NOT_SCROLL, true)
              }
            }
          }
        })

        updateRequests
          .debounce(200)
          .collectLatest {
            updateNow()
          }
      }
      finally {
        activatable.hideNotify()
      }
    }
  }

  companion object {
    fun allowScrolling(target: JComponent) {
      ClientProperty.remove(target, DO_NOT_SCROLL)
    }
  }

  override fun executePaint(component: Component?, g: Graphics2D?) {
    if (isVisible && glassPanel.isVisible) {
      glassPanel.paintSpotlight(g, target)
    }
  }

  override fun needsRepaint(): Boolean = true

  fun updateLater() {
    updateRequests.tryEmit(Unit)
  }

  fun updateNow() {
    updater(this)
  }

  open fun update(filter: SettingsFilter?, configurable: Configurable?, component: JComponent?) {
    if (configurable == null) {
      glassPanel.clear()
      isVisible = false
    }
    else if (component != null) {
      glassPanel.clear()
      val text = filter?.getSpotlightFilterText()
      isVisible = !text.isNullOrEmpty()
      val searchable = SearchableConfigurable.Delegate(configurable)
      try {
        lightOptions(configurable = searchable, component = component, option = text)
        // execute for empty string too
        val search = searchable.enableSearch(text)
        if (search != null && (filter == null || !filter.contains(configurable)) && text != configurableOption.get(searchable.id)) {
          search.run()
        }
      }
      finally {
        configurableOption.put(searchable.id, text)
      }
    }
    else if (!ApplicationManager.getApplication().isUnitTestMode()) {
      updateLater()
      return
    }

    fireNeedsRepaint(glassPanel)
  }
}

private fun center(component: JComponent): Boolean {
  var scrollPane: JScrollPane? = null
  var c: Component? = component
  while (c != null && c !is CellRendererPane) {
    if (c is JScrollPane) {
      scrollPane = c
    }
    // we need the topmost scroll pane descendant of the editor
    if (c is ConfigurableEditor) {
      break
    }

    c = c.getParent()
  }

  if (scrollPane == null) {
    return false
  }

  val viewport = scrollPane.getViewport()
  if (viewport == null || viewport.getHeight() <= 0) {
    return false
  }

  val view = viewport.view
  if (view !is JComponent) {
    return false
  }

  val bounds = SwingUtilities.convertRectangle(component.getParent(), component.bounds, view)
  val extraHeight = viewport.getHeight() - bounds.height
  if (extraHeight > 0) {
    bounds.y -= extraHeight / 2
    bounds.height += extraHeight
  }
  // horizontal scrolling usually does more harm than good
  bounds.x = 0
  bounds.width = viewport.getWidth()
  view.scrollRectToVisible(bounds)
  return true
}
