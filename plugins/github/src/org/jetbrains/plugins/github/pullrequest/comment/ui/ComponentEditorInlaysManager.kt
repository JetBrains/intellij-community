// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

class ComponentEditorInlaysManager(private val editor: EditorImpl) : Disposable {
  private val editorWidthUntilMargin: Float
  private val verticalScrollbarFlipped: Boolean

  init {
    val metrics = editor.getFontMetrics(Font.PLAIN)
    val spaceWidth = FontLayoutService.getInstance().charWidth2D(metrics, ' '.toInt())
    editorWidthUntilMargin = spaceWidth * (editor.settings.getRightMargin(editor.project)) + 1

    val scrollbarFlip = editor.scrollPane.getClientProperty(JBScrollPane.Flip::class.java)
    verticalScrollbarFlipped = scrollbarFlip == JBScrollPane.Flip.HORIZONTAL || scrollbarFlip == JBScrollPane.Flip.BOTH

    fun updateAllLocations() {
      getAllInlays().forEach { updateComponentWrapperLocation(it, it.renderer.component) }
    }

    editor.foldingModel.addListener(object : FoldingListener {
      override fun onFoldProcessingEnd() = ApplicationManager.getApplication().invokeLater {
        updateAllLocations()
      }
    }, this)

    editor.scrollPane.viewport.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        ApplicationManager.getApplication().invokeLater {
          getAllInlays().map { it.renderer.component }.forEach(::updateComponentWrapperWidth)
        }
      }
    })

    editor.inlayModel.addListener(object : InlayModel.Listener {
      override fun onAdded(inlay: Inlay<*>) = updateAllLocations()
      override fun onUpdated(inlay: Inlay<*>) = updateAllLocations()
      override fun onRemoved(inlay: Inlay<*>) = updateAllLocations()
    }, this)

    Disposer.register(editor.disposable, this)
  }

  //TODO: cache?
  private fun getAllInlays(): List<Inlay<out ComponentPlaceholder>> {
    return editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength - 1, ComponentPlaceholder::class.java)
      .filter { it.bounds != null }
  }

  fun addComponent(line: Int, component: JComponent) {
    if (Disposer.isDisposed(this)) return

    val componentWrapper = wrapComponent(component)

    val lineIndex = line - 1
    val documentOffset = editor.document.getLineEndOffset(lineIndex)
    val inlay = editor.inlayModel.addBlockElement(documentOffset, true, false, 1,
                                                  ComponentPlaceholder(componentWrapper))!!
    componentWrapper.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        ApplicationManager.getApplication().invokeLater {
          inlay.updateSize()
          updateComponentWrapperLocation(inlay, componentWrapper)
        }
      }
    })
    componentWrapper.addMouseWheelListener(editor.contentComponent::dispatchEvent)

    updateComponentWrapperWidth(componentWrapper)

    inlay.updateSize()
    updateComponentWrapperLocation(inlay, componentWrapper)

    editor.contentComponent.add(componentWrapper)

    ApplicationManager.getApplication().invokeLater {
      getAllInlays().forEach { updateComponentWrapperLocation(it, it.renderer.component) }
    }
  }

  private fun wrapComponent(component: JComponent): JBScrollPane {
    val scrollable = ScrollablePanel(BorderLayout()).apply {
      isOpaque = false
      add(component, BorderLayout.CENTER)
    }

    val scrollPane = object : JBScrollPane(scrollable) {
      override fun paint(g: Graphics) {
        // We need this fix with AlphaComposite.SrcOver to resolve problem of black background on transparent images such as icons.
        val oldComposite = (g as Graphics2D).composite
        g.composite = AlphaComposite.SrcOver
        super.paint(g)
        g.composite = oldComposite
      }
    }.apply {
      isOpaque = false
      viewport.isOpaque = false


      border = if (verticalScrollbarFlipped) JBUI.Borders.emptyLeft(editor.scrollPane.verticalScrollBar.width) else JBUI.Borders.empty()
      viewportBorder = JBUI.Borders.empty()

      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBar.preferredSize = Dimension(0, 0)
    }


    component.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        ApplicationManager.getApplication().invokeLater {
          updateComponentWrapperHeight(scrollPane)
        }
      }
    })

    return scrollPane
  }

  private fun updateComponentWrapperLocation(inlay: Inlay<out ComponentPlaceholder>, wrapper: JBScrollPane) {
    val bounds = inlay.bounds
    if (bounds == null) {
      wrapper.isVisible = false
    }
    else {
      wrapper.location = bounds.location
      wrapper.isVisible = true
      updateComponentWrapperWidth(wrapper)
    }
  }

  private fun updateComponentWrapperWidth(wrapper: JBScrollPane) {
    if (!wrapper.isVisible) return

    val scrollbarAdjustment = if (verticalScrollbarFlipped) editor.scrollPane.verticalScrollBar.width else 0
    val editorWidth = editor.scrollPane.viewport.width - scrollbarAdjustment
    val width = Math.min(editorWidth, editorWidthUntilMargin.toInt()) + scrollbarAdjustment

    wrapper.size = Dimension(width, wrapper.size.height)
  }

  private fun updateComponentWrapperHeight(wrapper: JBScrollPane) {
    if (!wrapper.isVisible) return

    val height = wrapper.viewport.components[0].height
    wrapper.size = Dimension(wrapper.size.width, height)
  }

  override fun dispose() {
    for (inlay in getAllInlays()) {
      val component = inlay.renderer.component
      component.parent.remove(component)
      inlay.dispose()
    }
  }

  private class ComponentPlaceholder(val component: JBScrollPane) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = component.width

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = component.height

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    }
  }
}