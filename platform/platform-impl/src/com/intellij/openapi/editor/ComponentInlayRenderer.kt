// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle

/**
 * Open implementation of [EditorCustomElementRenderer] for component-based inlays.
 * Can be extended to add context menu and/or gutter associated with inlay.
 * Use [Editor.addComponentInlay] to create and component inlay with the renderer.
 */
@Experimental
open class ComponentInlayRenderer<out T : Component>(val component: T,
                                                     val alignment: ComponentInlayAlignment? = null) : EditorCustomElementRenderer {
  internal var inlaySize: Dimension = component.preferredSize

  final override fun calcWidthInPixels(inlay: Inlay<*>): Int = inlaySize.width

  final override fun calcHeightInPixels(inlay: Inlay<*>): Int = inlaySize.height

  final override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) = Unit
}