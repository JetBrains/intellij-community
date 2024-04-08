// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html.image

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.DataUrl
import com.intellij.util.ui.StartupUiUtil
import java.awt.*
import java.net.URL
import javax.swing.Icon
import javax.swing.event.DocumentEvent
import javax.swing.text.*
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument
import kotlin.math.max

/**
 * Custom view for Swing HTML facility, displays [UnloadableAdaptiveImage]s
 */
//TODO insets, borders, alt view
open class AdaptiveImageView(elem: Element) : View(elem) {
  private val scaleContext: ScaleContext?
    get() = myCachedContainer?.takeIf(StartupUiUtil::isJreHiDPI)?.let(ScaleContext::create)

  private val sysScale: Float
    get() = scaleContext?.getScale(ScaleType.SYS_SCALE)?.toFloat() ?: 1f

  /**
   * [com.intellij.util.ui.html.HiDpiScalingImageView], which is currently used [com.intellij.codeInsight.documentation.DocumentationEditorPane]
   * treats raster images' dimensions as physical (ignoring scaling), so image pixel is the same as screen pixel
   * Correctness of that behavior is questionable since on small hi-dpi displays (like 4k 15.6") with huge (2.25+) scaling
   * those images become unreadable
   *
   * The '1.0f / sysScale' expression here reproduces that behavior.
   * Replace with just '1.0f' to treat image dimensions as logical dimensions (default behavior in all browsers)
   */
  private val imageToLogicalScale: Float
    get() {
      return when (val state = myState) {
        is ViewState.Idle, is ViewState.LoadError, is ViewState.SrcParseError -> 1.0f
        is ViewState.Loaded -> if (state.isVector) 1.0f else 1.0f / sysScale
      }
    }

  private var myImageRenderer: AdaptiveImageRenderer? = null
  private var myState: ViewState = ViewState.Idle()
  private var myCachedContainer: Container? = null
  private var myCachedDocument: Document? = null

  private var myPreferredImageViewDimensions: FloatDimensions = FloatDimensions(DEFAULT_WIDTH.toFloat(), DEFAULT_HEIGHT.toFloat())
  private var myBorder: Int = 0
  private var myVerticalAlign: Float = 0f
  private var myWidthAttrValue: Int = -1
  private var myHeightAttrValue: Int = -1

  init {
    updateStateFromAttrs()
  }

  private fun updateStateFromAttrs() {
    myBorder = getIntAttr(HTML.Attribute.BORDER, DEFAULT_BORDER)
    var alignment = element.attributes.getAttribute(HTML.Attribute.ALIGN)
    var vAlign = 1.0f
    if (alignment != null) {
      alignment = alignment.toString()
      if ("top" == alignment) {
        vAlign = 0f
      }
      else if ("middle" == alignment) {
        vAlign = .5f
      }
    }
    this.myVerticalAlign = vAlign

    myWidthAttrValue = getIntAttr(HTML.Attribute.WIDTH, -1)
    myHeightAttrValue = getIntAttr(HTML.Attribute.HEIGHT, -1)
    myImageRenderer?.setOrigin(getImageOrigin())

    updatePreferredImageViewDimensions()
  }

  private fun updatePreferredImageViewDimensions() : Boolean {
    val newDimensions = when (val state = myState) {
      is ViewState.Loaded -> {
        var logicalWidth = when (state.dims.width.unit) {
          ImageDimension.Unit.PX -> state.dims.width.value
          ImageDimension.Unit.EM -> state.dims.width.value * DEFAULT_PX_PER_EM
          ImageDimension.Unit.EX -> state.dims.width.value * DEFAULT_PX_PER_EX
          else -> null
        }
        var logicalHeight = when (state.dims.height.unit) {
          ImageDimension.Unit.PX -> state.dims.height.value
          ImageDimension.Unit.EM -> state.dims.height.value * DEFAULT_PX_PER_EM
          ImageDimension.Unit.EX -> state.dims.height.value * DEFAULT_PX_PER_EX
          else -> null
        }

        if (logicalHeight == null || logicalWidth == null) {
          logicalWidth = state.dims.fallBack.width
          logicalHeight = state.dims.fallBack.height
        }

        computeViewDimensions(logicalWidth, logicalHeight)
      }
      is ViewState.LoadError, is ViewState.SrcParseError -> {
        val icon = notLoadedIcon
        computeViewDimensions(icon.iconWidth.toFloat(), icon.iconHeight.toFloat())
      }
      is ViewState.Idle -> {
        val icon = loadingIcon
        computeViewDimensions(icon.iconWidth.toFloat(), icon.iconHeight.toFloat())
      }
    }

    if (newDimensions != myPreferredImageViewDimensions) {
      myPreferredImageViewDimensions = newDimensions
      return true
    } else {
      return false
    }
  }

  private fun updateRenderDims(logicalDims: FloatDimensions) {
    myImageRenderer?.setRenderConfig(logicalDims.width, logicalDims.height, sysScale)
  }

  private fun handleRendererEvent(evt: AdaptiveImageRendererEvent) {
    myState = when (evt) {
      is AdaptiveImageRendererEvent.Loaded -> ViewState.Loaded(evt.dimensions, evt.vector)
      is AdaptiveImageRendererEvent.Rasterized -> ViewState.Loaded(evt.dimensions, evt.vector)
      is AdaptiveImageRendererEvent.Error -> ViewState.LoadError()
      is AdaptiveImageRendererEvent.Unloaded -> ViewState.Idle()
    }

    if (updatePreferredImageViewDimensions()) {
      preferenceChanged(null, true, true)
    } else {
      container?.repaint()
    }
  }

  private fun getIntAttr(name: HTML.Attribute, defaultValue: Int): Int {
    val attr = element.attributes
    if (!attr.isDefined(name)) return defaultValue

    val value = attr.getAttribute(name) as? String ?: return defaultValue
    return try {
      max(0, value.toInt())
    }
    catch (x: NumberFormatException) {
      defaultValue
    }
  }

  private fun getImageOrigin(): AdaptiveImageOrigin? {
    val src = element.attributes.getAttribute(HTML.Attribute.SRC) as String? ?: return null
    val filteredSrc = src.filter { !it.isWhitespace() }
    if (DataUrl.isDataUrl(filteredSrc)) {
      try {
        return AdaptiveImageOrigin.DataUrl(DataUrl.parse(filteredSrc))
      }
      catch (e: Exception) {
        thisLogger().warn("Failed to parse data url", e)
        return null
      }
    }

    val docBase = (document as HTMLDocument).base
    try {
      return AdaptiveImageOrigin.Url(URL(docBase, filteredSrc).toString())
    }
    catch (e: Exception) {
      thisLogger().warn("Error generating image src URL", e)
      return null
    }
  }

  override fun getPreferredSpan(axis: Int): Float = when (axis) {
    X_AXIS -> (myPreferredImageViewDimensions.width * imageToLogicalScale) + 2 * myBorder
    Y_AXIS -> (myPreferredImageViewDimensions.height * imageToLogicalScale) + 2 * myBorder
    else -> throw IllegalArgumentException("Invalid axis: $axis")
  }

  override fun getToolTipText(x: Float, y: Float, allocation: Shape): String? =
    super.getElement().attributes.getAttribute(HTML.Attribute.ALT) as? String

  override fun paint(g: Graphics, alloc: Shape) {
    val bounds = alloc.bounds
    val contentWidth = bounds.width - 2 * myBorder
    val contentHeight = bounds.height - 2 * myBorder

    if (contentWidth <= 0 || contentHeight <= 0) return
    updateRenderDims(FloatDimensions(contentWidth.toFloat(), contentHeight.toFloat()))

    when (myState) {
      is ViewState.LoadError, is ViewState.SrcParseError -> {
        notLoadedIcon.paintIcon(null, g, bounds.x + myBorder, bounds.y + myBorder)
      }
      is ViewState.Idle, is ViewState.Loaded -> {
        val image = myImageRenderer?.getRenderedImage()
        if (image != null) {
          StartupUiUtil.drawImage(g, image, Rectangle(bounds.x + myBorder, bounds.y + myBorder, contentWidth, contentHeight), null)
        }
        else {
          loadingIcon.paintIcon(null, g, bounds.x + myBorder, bounds.y + myBorder)
        }
      }
    }
  }

  @Suppress("DuplicatedCode")
  override fun modelToView(pos: Int, a: Shape, b: Position.Bias): Shape? {
    val p0 = startOffset
    val p1 = endOffset
    if (pos in p0..p1) {
      val r = a.bounds
      if (pos == p1) {
        r.x += r.width
      }
      r.width = 0
      return r
    }
    return null
  }

  override fun viewToModel(x: Float, y: Float, a: Shape, bias: Array<Position.Bias>): Int {
    val alloc = a as Rectangle
    if (x < alloc.x + alloc.width) {
      bias[0] = Position.Bias.Forward
      return startOffset
    }
    bias[0] = Position.Bias.Backward
    return endOffset
  }

  override fun changedUpdate(e: DocumentEvent?, a: Shape?, f: ViewFactory?) {
    super.changedUpdate(e, a, f)
    updateStateFromAttrs()
    preferenceChanged(null, true, true)
  }

  override fun getAlignment(axis: Int): Float {
    return if (axis == Y_AXIS) myVerticalAlign else super.getAlignment(axis)
  }

  private fun computeViewDimensions(defaultWidth: Float, defaultHeight: Float): FloatDimensions {
    val aspectRatio: Double = defaultWidth.toDouble() / defaultHeight
    if (myWidthAttrValue >= 0 && myHeightAttrValue >= 0) {
      return FloatDimensions(myWidthAttrValue.toFloat(), myHeightAttrValue.toFloat())
    }
    else if (myWidthAttrValue >= 0) {
      return FloatDimensions(myWidthAttrValue.toFloat(), (myWidthAttrValue.toDouble() / aspectRatio).toFloat())
    }
    else if (myHeightAttrValue >= 0) {
      return FloatDimensions((myHeightAttrValue.toDouble() * aspectRatio).toFloat(), myHeightAttrValue.toFloat())
    }
    else {
      return FloatDimensions(defaultWidth, defaultHeight)
    }
  }

  override fun setParent(parent: View?) {
    super.setParent(parent)
    myCachedContainer = container
    myCachedDocument = document

    if (myImageRenderer == null) {
      val imagesManager = document.getProperty(ADAPTIVE_IMAGES_MANAGER_PROPERTY) as AdaptiveImagesManager?
      if (imagesManager != null) {
        myImageRenderer = imagesManager.createRenderer(this::handleRendererEvent)
        updateStateFromAttrs()
      }
    }
  }

  companion object {
    private const val DEFAULT_BORDER = 0
    const val DEFAULT_WIDTH = 32
    const val DEFAULT_HEIGHT = 32

    /**
     * Property name in [Document] where instance of [AdaptiveImagesManager] should be stored
     */
    const val ADAPTIVE_IMAGES_MANAGER_PROPERTY = "adaptiveImagesManager"
  }
}

private val loadingIcon: Icon
  get() = AllIcons.Process.Step_passive

private val notLoadedIcon: Icon
  get() = AllIcons.FileTypes.Image

//TODO use font size from container
const val DEFAULT_PX_PER_EM = 10.0f
const val DEFAULT_PX_PER_EX = 5.0f

sealed interface ViewState {
  class Idle : ViewState
  class Loaded(val dims: ImageDimensions, val isVector: Boolean) : ViewState
  class LoadError : ViewState
  class SrcParseError : ViewState
}

/**
 * Similar to [com.intellij.util.ui.html.FitToWidthImageView]
 */
internal class FitToWidthAdaptiveImageView(element: Element) : AdaptiveImageView(element) {
  private var myAvailableWidth = 0

  override fun getResizeWeight(axis: Int): Int =
    if (axis == X_AXIS) 1 else 0

  override fun getMaximumSpan(axis: Int): Float =
    getPreferredSpan(axis)

  override fun getPreferredSpan(axis: Int): Float {
    val baseSpan = super.getPreferredSpan(axis)
    if (axis == X_AXIS) {
      return baseSpan
    }
    else {
      var availableWidth = availableWidth
      if (availableWidth <= 0) return baseSpan
      val baseXSpan = super.getPreferredSpan(X_AXIS)
      if (baseXSpan <= 0) return baseSpan
      if (availableWidth > baseXSpan) {
        availableWidth = baseXSpan.toInt()
      }
      if (myAvailableWidth > 0 && availableWidth != myAvailableWidth) {
        preferenceChanged(null, false, true)
      }
      myAvailableWidth = availableWidth
      return baseSpan * availableWidth / baseXSpan
    }
  }

  private val availableWidth: Int
    get() {
      var v: View? = this
      while (v != null) {
        val parent = v.parent
        if (parent is FlowView) {
          val childCount = parent.getViewCount()
          for (i in 0 until childCount) {
            if (parent.getView(i) === v) {
              return parent.getFlowSpan(i)
            }
          }
        }
        v = parent
      }
      return 0
    }
}
