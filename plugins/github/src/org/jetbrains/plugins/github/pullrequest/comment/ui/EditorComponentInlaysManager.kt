// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.CalledInAwt
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

class EditorComponentInlaysManager(private val editor: EditorImpl) : Disposable {
  private val editorTextWidth: Int
  private val verticalScrollbarFlipped: Boolean

  private val managedInlays = mutableSetOf<Inlay<out ComponentWrapperPlaceholder>>()

  init {
    val metrics = editor.getFontMetrics(Font.PLAIN)
    val spaceWidth = FontLayoutService.getInstance().charWidth2D(metrics, ' '.toInt())
    editorTextWidth = (spaceWidth * (editor.settings.getRightMargin(editor.project))).toInt()

    val scrollbarFlip = editor.scrollPane.getClientProperty(JBScrollPane.Flip::class.java)
    verticalScrollbarFlipped = scrollbarFlip == JBScrollPane.Flip.HORIZONTAL || scrollbarFlip == JBScrollPane.Flip.BOTH

    editor.foldingModel.addListener(object : FoldingListener {
      override fun onFoldProcessingEnd() = ApplicationManager.getApplication().invokeLater {
        updateLocationForAllInlays()
      }
    }, this)

    val viewportResizeListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        ApplicationManager.getApplication().invokeLater {
          updateWidthForAllInlays()
        }
      }
    }
    editor.scrollPane.viewport.addComponentListener(viewportResizeListener)
    Disposer.register(this, Disposable {
      editor.scrollPane.viewport.removeComponentListener(viewportResizeListener)
    })

    editor.inlayModel.addListener(object : InlayModel.SimpleAdapter() {
      override fun onUpdated(inlay: Inlay<*>) = updateLocationForAllInlays()
    }, this)

    Disposer.register(editor.disposable, this)
  }

  private fun updateLocationForAllInlays() {
    managedInlays.forEach { updateWrapperLocation(it, it.renderer.wrapper) }
  }

  private fun updateWidthForAllInlays() {
    managedInlays.forEach { updateWrapperWidth(it.renderer.wrapper) }
  }

  @CalledInAwt
  fun insertAfter(line: Int, component: JComponent): Inlay<*>? {
    if (Disposer.isDisposed(this)) return null

    val offset = editor.document.getLineEndOffset(line - 1)
    return wrapAndInsert(offset, component)
  }

  private fun wrapAndInsert(offset: Int, component: JComponent): Inlay<*> {
    val wrapper = ComponentWrapper(component)
    val inlay = editor.inlayModel.addBlockElement(offset, true, false, 1,
                                                  ComponentWrapperPlaceholder(wrapper))!!

    component.background = editor.backgroundColor
    ApplicationManager.getApplication().messageBus.connect(inlay)
      .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { component.background = editor.backgroundColor })

    wrapper.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        ApplicationManager.getApplication().invokeLater {
          inlay.updateSize()
          updateWrapperLocation(inlay, wrapper)
        }
      }
    })
    wrapper.addMouseWheelListener(editor.contentComponent::dispatchEvent)

    updateWrapperWidth(wrapper)

    inlay.updateSize()
    updateWrapperLocation(inlay, wrapper)

    editor.contentComponent.add(wrapper)
    managedInlays.add(inlay)
    Disposer.register(inlay, Disposable {
      editor.contentComponent.remove(wrapper)
      managedInlays.remove(inlay)
    })
    return inlay
  }

  private fun updateWrapperLocation(inlay: Inlay<out ComponentWrapperPlaceholder>, wrapper: ComponentWrapper) {
    val bounds = inlay.bounds
    if (bounds == null) {
      wrapper.isVisible = false
    }
    else {
      wrapper.location = Point(if (verticalScrollbarFlipped) editor.scrollPane.verticalScrollBar.width else 0, bounds.location.y)
      wrapper.isVisible = true
      updateWrapperWidth(wrapper)
    }
  }

  private fun updateWrapperWidth(wrapper: ComponentWrapper) {
    if (!wrapper.isVisible) return

    val visibleEditorTextWidth = editor.scrollPane.viewport.width - editor.scrollPane.verticalScrollBar.width
    val width = Math.min(visibleEditorTextWidth, editorTextWidth)

    wrapper.size = Dimension(width, wrapper.size.height)
  }

  private class ComponentWrapper(val component: JComponent) : JBScrollPane() {
    init {
      isOpaque = false
      viewport.isOpaque = false

      border = JBUI.Borders.empty()
      viewportBorder = JBUI.Borders.empty()

      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBar.preferredSize = Dimension(0, 0)

      val panel = ScrollablePanel(BorderLayout()).apply {
        isOpaque = false

        add(component, BorderLayout.CENTER)
        addComponentListener(object : ComponentAdapter() {
          override fun componentResized(e: ComponentEvent) = ApplicationManager.getApplication().invokeLater {
            refreshHeight()
          }
        })
      }
      setViewportView(panel)
    }

    fun refreshHeight() {
      size = Dimension(width, viewport.view.height)
    }

    override fun paint(g: Graphics) {
      // We need this fix with AlphaComposite.SrcOver to resolve problem of black background on transparent images such as icons.
      val oldComposite = (g as Graphics2D).composite
      g.composite = AlphaComposite.SrcOver
      super.paint(g)
      g.composite = oldComposite
    }
  }

  override fun dispose() {
    val iter = managedInlays.iterator()
    while (iter.hasNext()) {
      val inlay = iter.next()
      iter.remove()
      Disposer.dispose(inlay)
    }
  }

  private class ComponentWrapperPlaceholder(val wrapper: ComponentWrapper) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = wrapper.width
    override fun calcHeightInPixels(inlay: Inlay<*>): Int = wrapper.height

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    }
  }
}