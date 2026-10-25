// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.devkit.icons.inspector.model.InspectorFormatting.number
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.simpleClassName
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ScalableIcon
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.rendering.DefaultScalingContext
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.platform.icons.impl.rendering.debug.RenderNode
import com.intellij.platform.icons.impl.rendering.layers.BaseImageIconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.IconLayerRenderer
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.ui.Graphics2DDelegate
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ImageObserver
import java.awt.image.RenderedImage
import java.awt.image.renderable.RenderableImage
import javax.swing.Icon
import kotlin.math.roundToInt

private val LOG = logger<IconSizing>()

/** What the tree builder knows about where the picked icon is shown, for computing [IconSizes]. */
internal class SizingContext(
  /** Device pixels per logical pixel of the picked component's screen; `null` if unknown. */
  val screenScale: Float?,
  /** Measured painted size (visible content) of the picked icon (see [PaintRecorder]); `null` if not measured. */
  val rootPainted: SizeValue?,
  /** Device-pixel frame of the measured paint of the picked icon (its drawn image's allocated size); `null` if unknown. */
  val rootPaintedFrame: Rectangle? = null,
) {
  companion object {
    val NONE: SizingContext = SizingContext(null, null)
  }
}

/** Source / device-pixel / painted sizes of inspector nodes. All functions are total: failures become UNAVAILABLE. */
internal object IconSizing {
  private val UNIT_SCALING = DefaultScalingContext(1f, 1f)

  fun unavailable(note: String): SizeValue = SizeValue(0, 0, SizeProvenance.UNAVAILABLE, note)

  private fun measured(dimensions: Dimensions, note: String) = SizeValue(dimensions.width, dimensions.height, SizeProvenance.MEASURED, note)

  private fun computed(width: Int, height: Int, note: String) = SizeValue(width, height, SizeProvenance.COMPUTED, note)

  private fun size(d: Dimensions?): String = if (d == null) "?" else "${d.width}×${d.height}"

  /** Unscaled size of a renderer: native image size for image layers, else its dimensions at density 1 without scale. */
  fun rendererSource(renderer: Any): SizeValue = try {
    when (renderer) {
      is BaseImageIconLayerRenderer -> {
        val image = renderer.image
        val width = image.width
        val height = image.height
        if (width != null && height != null) computed(width, height, "native image size")
        else renderer.layout.consumedSpace(UNIT_SCALING).let { computed(it.width, it.height, "image size unknown; layer at density 1") }
      }
      is IconRenderer -> {
        val dimensions = renderer.calculateUsedDimensions(UNIT_SCALING)
        computed(dimensions.width, dimensions.height, "renderer at density 1, no scale")
      }
      is IconLayerRenderer -> {
        val dimensions = renderer.layout.consumedSpace(UNIT_SCALING)
        computed(dimensions.width, dimensions.height, "layer at density 1, no scale")
      }
      else -> unavailable("unknown renderer ${simpleClassName(renderer)}")
    }
  }
  catch (t: Throwable) {
    LOG.debug(t)
    unavailable("failed: $t")
  }

  /** Sizes of a captured renderer node; [topLevel] is the node of the capture's top-level renderer. */
  fun forRenderNode(node: RenderNode, capture: RenderCapture?, topLevel: Boolean): IconSizes {
    val scaling = node.scaling
    val dims = node.dimensions
    val device = if (dims == null) unavailable("no dimensions recorded")
    else measured(dims, "rendered at density ${number(scaling.displayDensity)} × context scale ${number(scaling.contextScale)}")
    val painted = when {
      topLevel && capture != null -> {
        val info = capture.paintInfo
        val what = if (capture.isCompose) "Compose draw" else "SwingIcon.paintIcon"
        val frameOnly = if (capture.isCompose) "; frame only, visible content not measured for Compose" else ""
        SizeValue(info.finalWidth, info.finalHeight, SizeProvenance.MEASURED, "final size (frame) of the captured $what$frameOnly")
      }
      dims != null -> measured(dims, "rendered size in the captured paint")
      else -> unavailable("no dimensions recorded")
    }
    return IconSizes(rendererSource(node.renderer), device, painted)
  }

  /** Sizes of a descriptor shown without a capture (static tree): only the source size can be computed. */
  fun forStaticDescriptor(descriptor: IconDescriptor): IconSizes {
    val source = try {
      IconRenderDebugger.untraced {
        val renderer = IconRendererManager.getInstance().createRenderer(descriptor, IconRendererManager.createRenderingContext())
        renderer.calculateUsedDimensions(UNIT_SCALING).let { computed(it.width, it.height, "fresh renderer at density 1, no scale") }
      }
    }
    catch (t: Throwable) {
      LOG.debug(t)
      unavailable("failed: $t")
    }
    val notPainted = unavailable("not painted since the pick")
    return IconSizes(source, notPainted, notPainted)
  }

  /** Static layer node (no renderer to ask). */
  fun forStaticLayer(): IconSizes {
    val notPainted = unavailable("not painted since the pick")
    return IconSizes(unavailable("not rendered since the pick"), notPainted, notPainted)
  }

  /**
   * The picked [SwingIcon] root: source = its descriptor at density 1 without its scale; device pixels = the captured top-level
   * render; painted = measured paint of the component (or the captured final size).
   */
  fun forSwingIconRoot(icon: SwingIcon, capture: RenderCapture?, context: SizingContext, isPicked: Boolean): IconSizes {
    val source = capture?.iconRenderer?.let(::rendererSource)?.let { it.copy(note = "descriptor at density 1, without the icon scale") }
                 ?: forStaticDescriptor(icon.iconDescriptor).source
    val root = capture?.root
    val device = root?.dimensions?.let { measured(it, "captured top-level render (density ${number(root.scaling.displayDensity)})") }
                 ?: legacyDevice(icon, context)
    val painted = when {
      isPicked && context.rootPainted != null -> context.rootPainted
      capture != null -> SizeValue(capture.paintInfo.finalWidth, capture.paintInfo.finalHeight, SizeProvenance.MEASURED,
                                   "final size of the captured SwingIcon.paintIcon")
      else -> unavailable("not painted since the pick")
    }
    return IconSizes(source, device, painted)
  }

  /** A legacy Swing icon: logical sizes and the screen scale; painted is only known for the picked icon itself. */
  fun forLegacy(icon: Icon, context: SizingContext, isPicked: Boolean): IconSizes {
    val source = try {
      val width = icon.iconWidth
      val height = icon.iconHeight
      val scale = (icon as? ScalableIcon)?.scale ?: 1f
      if (icon is ScalableIcon && scale > 0f && scale != 1f) {
        computed((width / scale).roundToInt(), (height / scale).roundToInt(), "logical ${width}×$height ÷ object scale ${number(scale)}")
      }
      else computed(width, height, "logical size")
    }
    catch (t: Throwable) {
      unavailable("failed: $t")
    }
    val painted = if (isPicked) context.rootPainted ?: unavailable("paint not measured")
    else unavailable("not attributable: only the picked icon's paint is measured")
    return IconSizes(source, legacyDevice(icon, context), painted)
  }

  private fun legacyDevice(icon: Icon, context: SizingContext): SizeValue {
    val scale = context.screenScale ?: return unavailable("screen scale unknown")
    return try {
      val width = icon.iconWidth
      val height = icon.iconHeight
      computed((width * scale).roundToInt(), (height * scale).roundToInt(), "logical ${width}×$height × screen scale ${number(scale)}")
    }
    catch (t: Throwable) {
      unavailable("failed: $t")
    }
  }

  /** A Compose element that is not rendered by the icons API: only its layout bounds are known. */
  fun forComposeSemantics(semantics: ComposeSemanticsImage?): IconSizes {
    val bounds = if (semantics == null) unavailable("no semantics bounds")
    else computed((semantics.right - semantics.left).roundToInt(), (semantics.bottom - semantics.top).roundToInt(),
                  "layout bounds from semantics (not a measured paint)")
    return IconSizes(unavailable("not rendered by the icons API"), bounds, bounds)
  }

  /** Device pixels per logical pixel of [component]'s screen. */
  fun screenScale(component: Component?): Float? {
    if (component == null) return null
    val transformScale = component.graphicsConfiguration?.defaultTransform?.scaleX?.toFloat()
    return if (transformScale != null && transformScale > 0f) transformScale else JBUIScale.sysScale(component)
  }
}

/**
 * Measures the size an icon really reaches the screen with: repaints the picked component into an offscreen image through a
 * recording [Graphics2D] that notes the device-pixel bounds of every image draw (icons draw images; backgrounds and borders
 * are fills and strokes). Looks that resize icons at paint time (e.g. the main toolbar's `HeaderToolbarButtonLook`) are thus
 * measured without platform hooks. The paint runs [IconRenderDebugger.untraced], so it is neither captured nor a flag hit.
 */
internal object PaintRecorder {
  private const val MAX_SIZE = 4096

  /**
   * One recorded image draw, in device pixels: [bounds] is the destination frame, [content] the bounding box of the pixels
   * the draw actually made visible (alpha > 0), `null` if it drew nothing visible.
   */
  data class Draw(val bounds: Rectangle, val content: Rectangle? = null)

  /** Measured painted size (visible content) of the icon of [picked], or `null` if nothing could be measured. EDT only. */
  fun measure(picked: PickedIcon): SizeValue? = measureWithFrame(picked)?.first

  /** Like [measure], plus the device-pixel frame of the attributed image draw. EDT only. */
  fun measureWithFrame(picked: PickedIcon): Pair<SizeValue, Rectangle>? {
    if (picked.compose != null) return null
    val target = picked.captureScope
    val point = if (picked.captureScope !== picked.component && picked.highlightBounds != null) {
      Point(picked.point.x - picked.highlightBounds.x, picked.point.y - picked.highlightBounds.y)
    }
    else picked.point
    val draws = try {
      record(target, IconSizing.screenScale(picked.component) ?: 1f)
    }
    catch (t: Throwable) {
      LOG.debug("Cannot measure the paint of ${target.javaClass.name}", t)
      return null
    }
    val scale = IconSizing.screenScale(picked.component) ?: 1f
    val value = choose(draws, point, scale) ?: return null
    return value to chooseDraw(draws, point, scale)!!.bounds
  }

  /** Paints [component] offscreen at [scale] and returns its image draws. */
  fun record(component: Component, scale: Float): List<Draw> {
    val width = component.width
    val height = component.height
    if (width <= 0 || height <= 0) return emptyList()
    val imageWidth = (width * scale).roundToInt().coerceIn(1, MAX_SIZE)
    val imageHeight = (height * scale).roundToInt().coerceIn(1, MAX_SIZE)
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
    val base = image.createGraphics()
    val draws = ArrayList<Draw>()
    try {
      base.scale(scale.toDouble(), scale.toDouble())
      base.clip = Rectangle(0, 0, width, height)
      val recording = RecordingGraphics(base, draws)
      IconRenderDebugger.untraced { component.paint(recording) }
    }
    finally {
      base.dispose()
    }
    return draws
  }

  /**
   * The image draw of the icon: the smallest draw (by frame) containing [point] (logical, component coordinates), else the
   * largest. Painted = its visible content; the frame (the image's allocated size, including transparent margins) is in the
   * note.
   */
  fun chooseDraw(draws: List<Draw>, point: Point, scale: Float): Draw? {
    val nonEmpty = draws.filter { it.bounds.width > 0 && it.bounds.height > 0 }
    if (nonEmpty.isEmpty()) return null
    val devicePoint = Point((point.x * scale).roundToInt(), (point.y * scale).roundToInt())
    val containing = nonEmpty.filter { it.bounds.contains(devicePoint) }
    return containing.minByOrNull { it.bounds.area() } ?: nonEmpty.maxBy { it.bounds.area() }
  }

  fun choose(draws: List<Draw>, point: Point, scale: Float): SizeValue? {
    val nonEmpty = draws.filter { it.bounds.width > 0 && it.bounds.height > 0 }
    val chosen = chooseDraw(draws, point, scale) ?: return null
    val devicePoint = Point((point.x * scale).roundToInt(), (point.y * scale).roundToInt())
    val containing = nonEmpty.filter { it.bounds.contains(devicePoint) }
    val how = if (containing.isNotEmpty()) "image draw under the picked point" else "largest image draw"
    val count = "${nonEmpty.size} image draw${if (nonEmpty.size == 1) "" else "s"}"
    val frame = chosen.bounds
    val content = chosen.content
    if (content == null) {
      return SizeValue(frame.width, frame.height, SizeProvenance.MEASURED,
                       "frame ${frame.width}×${frame.height} (no visible pixels found; frame only); recorded repaint: $how ($count)")
    }
    return SizeValue(content.width, content.height, SizeProvenance.MEASURED,
                     "frame ${frame.width}×${frame.height}, content ${content.width}×${content.height} at " +
                     "(${content.x - frame.x}, ${content.y - frame.y}) in the frame (alpha > 0); recorded repaint: $how ($count)")
  }

  private fun Rectangle.area(): Long = width.toLong() * height

  /** Bounding box of the pixels with alpha > 0, or `null`. */
  fun alphaBounds(image: BufferedImage): Rectangle? {
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = -1
    var maxY = -1
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        if ((image.getRGB(x, y) ushr 24) != 0) {
          if (x < minX) minX = x
          if (y < minY) minY = y
          if (x > maxX) maxX = x
          if (y > maxY) maxY = y
        }
      }
    }
    return if (maxX < 0) null else Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
  }

  /**
   * Delegating graphics that records image draws; [create] keeps recording into the same list. Each image draw is also
   * replayed alone into a transparent scratch image covering its device frame, to find its visible content.
   */
  private class RecordingGraphics(private val g: Graphics2D, private val draws: MutableList<Draw>) : Graphics2DDelegate(g) {
    override fun create(): Graphics = RecordingGraphics(g.create() as Graphics2D, draws)

    private fun record(x: Double, y: Double, width: Double, height: Double, extra: AffineTransform? = null,
                       replay: (Graphics2D) -> Unit) {
      if (width <= 0 || height <= 0) return
      val transform = AffineTransform(g.transform)
      if (extra != null) transform.concatenate(extra)
      val bounds2D = transform.createTransformedShape(Rectangle2D.Double(x, y, width, height)).bounds2D
      val frame = Rectangle(bounds2D.x.roundToInt(), bounds2D.y.roundToInt(), bounds2D.width.roundToInt(), bounds2D.height.roundToInt())
      draws.add(Draw(frame, content(frame, replay)))
    }

    private fun content(frame: Rectangle, replay: (Graphics2D) -> Unit): Rectangle? {
      if (frame.width <= 0 || frame.height <= 0 || frame.width > MAX_SIZE || frame.height > MAX_SIZE) return null
      return try {
        @Suppress("UndesirableClassUsage")
        val scratch = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
        val sg = scratch.createGraphics()
        try {
          sg.setRenderingHints(g.renderingHints)
          sg.translate(-frame.x.toDouble(), -frame.y.toDouble())
          sg.transform(g.transform)
          replay(sg)
        }
        finally {
          sg.dispose()
        }
        alphaBounds(scratch)?.let { Rectangle(it.x + frame.x, it.y + frame.y, it.width, it.height) }
      }
      catch (t: Throwable) {
        LOG.debug(t)
        null
      }
    }

    private fun Image.size(): Pair<Int, Int> = getWidth(null) to getHeight(null)

    override fun drawImage(img: BufferedImage?, op: BufferedImageOp?, x: Int, y: Int) {
      if (img != null) record(x.toDouble(), y.toDouble(), img.width.toDouble(), img.height.toDouble()) { it.drawImage(img, op, x, y) }
      super.drawImage(img, op, x, y)
    }

    override fun drawImage(img: Image?, dx1: Int, dy1: Int, dx2: Int, dy2: Int, sx1: Int, sy1: Int, sx2: Int, sy2: Int,
                           bgcolor: java.awt.Color?, observer: ImageObserver?): Boolean {
      recordRect(dx1, dy1, dx2, dy2) { it.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, null) }
      return super.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer)
    }

    override fun drawImage(img: Image?, dx1: Int, dy1: Int, dx2: Int, dy2: Int, sx1: Int, sy1: Int, sx2: Int, sy2: Int,
                           observer: ImageObserver?): Boolean {
      recordRect(dx1, dy1, dx2, dy2) { it.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null) }
      return super.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)
    }

    private fun recordRect(dx1: Int, dy1: Int, dx2: Int, dy2: Int, replay: (Graphics2D) -> Unit) {
      record(minOf(dx1, dx2).toDouble(), minOf(dy1, dy2).toDouble(), Math.abs(dx2 - dx1).toDouble(), Math.abs(dy2 - dy1).toDouble(),
             replay = replay)
    }

    override fun drawImage(img: Image?, x: Int, y: Int, bgcolor: java.awt.Color?, observer: ImageObserver?): Boolean {
      img?.size()?.let { (w, h) ->
        record(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble()) { it.drawImage(img, x, y, bgcolor, null) }
      }
      return super.drawImage(img, x, y, bgcolor, observer)
    }

    override fun drawImage(img: Image?, x: Int, y: Int, observer: ImageObserver?): Boolean {
      img?.size()?.let { (w, h) -> record(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble()) { it.drawImage(img, x, y, null) } }
      return super.drawImage(img, x, y, observer)
    }

    override fun drawImage(img: Image?, x: Int, y: Int, width: Int, height: Int, bgcolor: java.awt.Color?,
                           observer: ImageObserver?): Boolean {
      record(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()) { it.drawImage(img, x, y, width, height, bgcolor, null) }
      return super.drawImage(img, x, y, width, height, bgcolor, observer)
    }

    override fun drawImage(img: Image?, x: Int, y: Int, width: Int, height: Int, observer: ImageObserver?): Boolean {
      record(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()) { it.drawImage(img, x, y, width, height, null) }
      return super.drawImage(img, x, y, width, height, observer)
    }

    override fun drawImage(img: Image?, xform: AffineTransform?, obs: ImageObserver?): Boolean {
      img?.size()?.let { (w, h) -> record(0.0, 0.0, w.toDouble(), h.toDouble(), xform) { it.drawImage(img, xform, null) } }
      return super.drawImage(img, xform, obs)
    }

    override fun drawRenderedImage(img: RenderedImage?, xform: AffineTransform?) {
      if (img != null) record(0.0, 0.0, img.width.toDouble(), img.height.toDouble(), xform) { it.drawRenderedImage(img, xform) }
      super.drawRenderedImage(img, xform)
    }

    override fun drawRenderableImage(img: RenderableImage?, xform: AffineTransform?) {
      if (img != null) record(0.0, 0.0, img.width.toDouble(), img.height.toDouble(), xform) { it.drawRenderableImage(img, xform) }
      super.drawRenderableImage(img, xform)
    }
  }
}
