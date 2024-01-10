// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.ui.components.JBScrollPane
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.RepaintManager
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min

/**
 * Manager for component inlays. Joins a component and a block inlay together with synced positions and lifetimes.
 */
@Experimental
internal object ComponentInlayManager {
  fun <T : Component> add(editor: Editor,
                          offset: Int,
                          properties: InlayProperties,
                          renderer: ComponentInlayRenderer<T>): Inlay<ComponentInlayRenderer<T>>? =
    editor.inlayModel.addBlockElement(offset, properties, renderer)?.also {
      @Suppress("UNCHECKED_CAST")
      ComponentInlaysContainer.addInlay(it as Inlay<ComponentInlayRenderer<*>>)
    }
}

/**
 * Container for component inlays. Has one instance of the container per editor.
 * Conducting all synchronization between components and associated inlays, for components works as a regular AWT container.
 * For components supports different alignment modes:
 * - null - uses preferred width as an inlay width
 * - [ComponentInlayAlignment.STRETCH_TO_CONTENT_WIDTH] - uses preferred width as an inlay width, sets component width to editor content width during layout
 * - [ComponentInlayAlignment.FIT_CONTENT_WIDTH] - uses min width as an inlay width, sets component width to editor content width during layout
 * - [ComponentInlayAlignment.FIT_VIEWPORT_WIDTH] and [ComponentInlayAlignment.FIT_VIEWPORT_X_SPAN] - uses min width as an inlay width, sets component width to viewport width. For X Span shifts component to viewport.
 * Inlay width incorporated during content preferred width calculation and for that reason we can't sync inlay width with component actual width when alignment used,
 * otherwise editor content won't shrink when text changed or viewport changed.
 * I.e. we have component preferred width 10 and editor content width 100 (because of viewport) and FIT_CONTENT_WIDTH alignment,
 * after layout component's with will be 100 and if inlay width also 100 then after shrinking viewport to 80 the content width still will be 100 (because of inlay).
 * But if we set inlay width to preferred component width 10 then it will adjust to viewport size and resize component to width 80.
 */
private class ComponentInlaysContainer private constructor(val editor: EditorEx) : JComponent(), EditorHostedComponent, Disposable {
  companion object {
    private val INLAYS_CONTAINER = Key<ComponentInlaysContainer>("INLAYS_CONTAINER")

    fun addInlay(inlay: Inlay<ComponentInlayRenderer<*>>) {
      val editor = inlay.editor as EditorEx
      val inlaysContainer = editor.getUserData(INLAYS_CONTAINER) ?: ComponentInlaysContainer(editor).also { container ->
        editor.putUserData(INLAYS_CONTAINER, container)
        editor.contentComponent.add(container)
        EditorUtil.disposeWithEditor(editor, container)
        container.whenDisposed {
          editor.contentComponent.remove(container)
          editor.removeUserData(INLAYS_CONTAINER)
        }
      }
      if (!AppMode.isRemoteDevHost()) {
        inlaysContainer.add(inlay)
      }
      Disposer.register(inlaysContainer, inlay)
      inlay.whenDisposed {
        // auto-dispose container when last inlay removed
        if (inlaysContainer.remove(inlay) && inlaysContainer.inlays.isEmpty()) {
          Disposer.dispose(inlaysContainer)
        }
      }
    }
  }

  private var visibleAreaAwareInlaysCount = 0
  private var contentSizeAwareInlayCount = 0

  private val inlays = mutableListOf<Inlay<ComponentInlayRenderer<*>>>()
  private val contentResizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      revalidate()
      repaint()
    }
  }
  private val visibleAreaListener = VisibleAreaListener {
    revalidate()
    repaint()
  }
  private val foldingListener = object : FoldingListener {
    override fun onFoldProcessingEnd() {
      revalidate()
      repaint()
    }
  }

  override val isInputFocusOwner = true

  init {
    editor.foldingModel.addListener(foldingListener, this)
  }

  private fun remove(inlay: Inlay<ComponentInlayRenderer<*>>): Boolean {
    if (!inlays.remove(inlay))
      return false

    val renderer = inlay.renderer
    when (renderer.alignment) {
      ComponentInlayAlignment.FIT_CONTENT_WIDTH, ComponentInlayAlignment.STRETCH_TO_CONTENT_WIDTH -> {
        if (--contentSizeAwareInlayCount == 0) {
          editor.contentComponent.removeComponentListener(contentResizeListener)
        }
      }
      ComponentInlayAlignment.FIT_VIEWPORT_X_SPAN, ComponentInlayAlignment.FIT_VIEWPORT_WIDTH -> {
        if (--visibleAreaAwareInlaysCount == 0) {
          editor.scrollingModel.removeVisibleAreaListener(visibleAreaListener)
        }
      }
      else -> Unit
    }
    remove(renderer.component)
    return true
  }

  private fun add(inlay: Inlay<ComponentInlayRenderer<*>>) {
    val renderer = inlay.renderer
    inlays.add(inlay)
    add(renderer.component)
    renderer.component.isVisible = !EditorUtil.isInlayFolded(inlay)
    // optionally add listeners if any inlay aware of viewport or content size
    when (inlay.renderer.alignment) {
      ComponentInlayAlignment.FIT_CONTENT_WIDTH, ComponentInlayAlignment.STRETCH_TO_CONTENT_WIDTH -> {
        if (contentSizeAwareInlayCount++ == 0) {
          editor.contentComponent.addComponentListener(contentResizeListener)
        }
      }
      ComponentInlayAlignment.FIT_VIEWPORT_X_SPAN, ComponentInlayAlignment.FIT_VIEWPORT_WIDTH -> {
        if (visibleAreaAwareInlaysCount++ == 0) {
          editor.scrollingModel.addVisibleAreaListener(visibleAreaListener)
        }
      }
      else -> Unit
    }
  }

  override fun invalidate() {
    if (!isValid) return

    super.invalidate()
    // Effectively revalidate InlayComponent when invalidated.
    // Need to add to RepaintManager because otherwise component may not be validated until parent revalidate happen.
    // The way how Swing works it always invalidates parent component, but uses first validation root in hierarchy for repaint.
    // I.e. for editor -> scrollPane (validationRoot1) -> inlayComponent (validationRoot2) for editor.revalidate() it will mark inlayComponent as invalid,
    // but only validate scrollPane on repaint. We then have to wait for another revalidate call in hierarchy starting from inlayComponent.
    RepaintManager.currentManager(this).addInvalidComponent(this)
  }

  override fun isValidateRoot(): Boolean = true

  override fun doLayout() {
    val inlays = inlays
    if (inlays.isEmpty()) return

    val content = editor.contentComponent
    val initialContentWidth = content.width
    val scrollPane = editor.scrollPane
    val viewportReservedWidth = if (!isVerticalScrollbarFlipped(scrollPane)) editor.scrollPane.verticalScrollBar.width + content.insets.left
    else content.insets.left
    val visibleArea = scrollPane.viewport.visibleRect

    // Step 1: Sync inlay size with preferred component size.
    // Step 1.1: Update inlay size, it may fail in batch mode so need to do it in separate loop
    for (inlay in inlays) {
      inlay.renderer.let {
        it.inlaySize = when (it.alignment) {
          ComponentInlayAlignment.FIT_CONTENT_WIDTH -> {
            it.component.run { Dimension(minimumSize.width, preferredSize.height) }
          }
          ComponentInlayAlignment.FIT_VIEWPORT_X_SPAN, ComponentInlayAlignment.FIT_VIEWPORT_WIDTH -> {
            it.component.run { Dimension(minimumSize.width + viewportReservedWidth, preferredSize.height) }
          }
          else -> {
            it.component.preferredSize
          }
        }
      }
    }

    // Step 1.2: Update all inlays in a single batch for performance reasons
    // Do it as read action, because inlay callbacks may access document model
    ReadAction.run<Throwable> {
      editor.inlayModel.execute(true) {
        for (inlay in inlays) {
          inlay.renderer.let {
            if (it.inlaySize.width != inlay.widthInPixels || it.inlaySize.height != inlay.heightInPixels) {
              inlay.update()
            }
          }
        }
      }
    }

    // Step 2: Way editor implemented now it will validate size after inlay update and set it to preferred size which may be less than viewport.
    // We ask parent to layout editor in that case to restore it's size.
    if (content.width < initialContentWidth && content.width < visibleArea.width) {
      content.size = Dimension(min(initialContentWidth, visibleArea.width), content.height)
    }

    // Step 3: Set bounds of container to bounds of inner area (without insets) of editor. It defines a viewport for inlay components.
    bounds = SwingUtilities.calculateInnerArea(content, null)

    // Step 4: Layout inlay components
    // Do as read action, because com.intellij.openapi.editor.Inlay.getBounds requires it
    ReadAction.run<Throwable> {
      for (inlay in inlays) {
        val component = inlay.renderer.component
        val componentBounds = inlay.bounds

        if (componentBounds == null) {
          component.isVisible = false
          // component.bounds = Rectangle(0, 0, 0, 0)
        }
        else {
          component.isVisible = true
          val alignment = inlay.renderer.alignment

          // x in inlay bounds contains left gap of content, which we do not need
          componentBounds.x = if (alignment == ComponentInlayAlignment.FIT_VIEWPORT_X_SPAN) visibleArea.x else 0

          if (alignment == ComponentInlayAlignment.STRETCH_TO_CONTENT_WIDTH || alignment == ComponentInlayAlignment.FIT_CONTENT_WIDTH) {
            componentBounds.width = bounds.width
          }
          else if (alignment == ComponentInlayAlignment.FIT_VIEWPORT_WIDTH || alignment == ComponentInlayAlignment.FIT_VIEWPORT_X_SPAN) {
            componentBounds.width = max(component.minimumSize.width, visibleArea.width - viewportReservedWidth)
          }

          component.bounds = componentBounds
        }
      }
    }
  }

  private fun isVerticalScrollbarFlipped(scrollPane: JScrollPane): Boolean {
    val flipProperty = scrollPane.getClientProperty(JBScrollPane.Flip::class.java)
    return flipProperty === JBScrollPane.Flip.HORIZONTAL || flipProperty === JBScrollPane.Flip.BOTH
  }

  override fun dispose() = Unit
}
