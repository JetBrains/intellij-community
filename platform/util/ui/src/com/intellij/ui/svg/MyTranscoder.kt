// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.svg

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.ImageLoader
import org.apache.batik.anim.dom.SVGOMDocument
import org.apache.batik.bridge.*
import org.apache.batik.bridge.svg12.SVG12BridgeContext
import org.apache.batik.ext.awt.image.GraphicsUtil
import org.apache.batik.gvt.renderer.MacRenderer
import org.apache.batik.gvt.renderer.StaticRenderer
import org.apache.batik.transcoder.SVGAbstractTranscoder
import org.apache.batik.transcoder.TranscoderException
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.util.ParsedURL
import org.apache.batik.util.SVGConstants
import org.jetbrains.annotations.ApiStatus
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.svg.SVGDocument
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.StringReader
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class MyTranscoder(private val scale: Float) : SVGAbstractTranscoder() {
  companion object {
    @JvmStatic
    val iconMaxSize: Float by lazy {
      var maxSize = Integer.MAX_VALUE.toFloat()
      if (!GraphicsEnvironment.isHeadless()) {
        val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val bounds = device.defaultConfiguration.bounds
        val tx = device.defaultConfiguration.defaultTransform
        maxSize = max(bounds.width * tx.scaleX, bounds.height * tx.scaleY).toInt().toFloat()
      }
      maxSize
    }

    @Throws(TranscoderException::class)
    @JvmStatic
    @JvmOverloads
    fun createImage(scale: Float,
                    document: Document,
                    outDimensions: ImageLoader.Dimension2DDouble? /*OUT*/,
                    overriddenWidth: Float = -1f,
                    overriddenHeight: Float = -1f): BufferedImage {
      val transcoder = MyTranscoder(scale)
      if (overriddenWidth != -1f) {
        transcoder.addTranscodingHint(KEY_WIDTH, overriddenWidth)
      }
      if (overriddenHeight != -1f) {
        transcoder.addTranscodingHint(KEY_HEIGHT, overriddenHeight)
      }

      val iconMaxSize = iconMaxSize
      transcoder.addTranscodingHint(KEY_MAX_WIDTH, iconMaxSize)
      transcoder.addTranscodingHint(KEY_MAX_HEIGHT, iconMaxSize)

      transcoder.transcode(document, document.documentURI, null)

      // prepare the image to be painted
      val w = (transcoder.width + 0.5).toInt()
      val h = (transcoder.height + 0.5).toInt()

      // paint the SVG document using the bridge package
      // create the appropriate renderer
      val renderer = if (SystemInfoRt.isMac) MacRenderer() else StaticRenderer()
      val renderingHints = renderer.renderingHints
      renderingHints.add(RenderingHints(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE))
      renderer.renderingHints = renderingHints

      renderer.updateOffScreen(w, h)
      renderer.transform = transcoder.curTxf
      renderer.tree = transcoder.root
      transcoder.root = null
      try {
        // now we are sure that the aoi is the image size
        @Suppress("SpellCheckingInspection")
        val raoi = Rectangle2D.Float(0f, 0f, transcoder.width, transcoder.height)
        // renderer's AOI must be in user space
        renderer.repaint(transcoder.curTxf.createInverse().createTransformedShape(raoi))
        @Suppress("UndesirableClassUsage")
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g2d = GraphicsUtil.createGraphics(image)
        g2d.drawRenderedImage(renderer.offScreen, AffineTransform())
        g2d.dispose()

        outDimensions?.setSize(transcoder.origDocWidth.toDouble(), transcoder.origDocHeight.toDouble())
        return image
      }
      catch (e: TranscoderException) {
        throw e
      }
      catch (e: Exception) {
        throw TranscoderException(e)
      }
      finally {
        transcoder.ctx?.dispose()
      }
    }
  }

  private var origDocWidth = 0f
  private var origDocHeight = 0f

  init {
    width = 16f
    height = 16f
  }

  override fun transcode(document: Document, uri: String?, output: TranscoderOutput?) {
    if (hints.containsKey(KEY_WIDTH)) {
      width = hints.get(KEY_WIDTH) as Float
    }
    if (hints.containsKey(KEY_HEIGHT)) {
      height = hints.get(KEY_HEIGHT) as Float
    }

    val svgDoc = document as SVGOMDocument
    ctx = createBridgeContext(svgDoc)

    // build the GVT tree
    builder = GVTBuilder()
    val gvtRoot = builder.build(ctx, svgDoc)
    // get the 'width' and 'height' attributes of the SVG document
    val docWidth = ctx.documentSize.width.toFloat()
    val docHeight = ctx.documentSize.height.toFloat()
    setImageSize(docWidth, docHeight)

    // compute the preserveAspectRatio matrix
    val preserveAspectRatioMatrix: AffineTransform

    // take the AOI into account if any
    if (hints.containsKey(KEY_AOI)) {
      val aoi = (hints[KEY_AOI] as Rectangle2D?)!!
      // transform the AOI into the image's coordinate system
      preserveAspectRatioMatrix = AffineTransform()
      val sx = width / aoi.width
      val sy = height / aoi.height
      val scale = min(sx, sy)
      preserveAspectRatioMatrix.scale(scale, scale)
      val tx = -aoi.x + (width / scale - aoi.width) / 2
      val ty = -aoi.y + (height / scale - aoi.height) / 2
      preserveAspectRatioMatrix.translate(tx, ty)
      // take the AOI transformation matrix into account
      // we apply first the preserveAspectRatio matrix
      curAOI = aoi
    }
    else {
      val root = svgDoc.rootElement

      val viewBox = root.getAttributeNS(null, SVGConstants.SVG_VIEW_BOX_ATTRIBUTE)
      if (viewBox != null && viewBox.isNotEmpty()) {
        val aspectRatio = root.getAttributeNS(null, SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE)
        preserveAspectRatioMatrix = ViewBox.getPreserveAspectRatioTransform(root, viewBox, aspectRatio, width, height, ctx)
      }
      else {
        // no viewBox has been specified, create a scale transform
        val scale = min(width / docWidth, height / docHeight)
        preserveAspectRatioMatrix = AffineTransform.getScaleInstance(scale.toDouble(), scale.toDouble())
      }
      curAOI = Rectangle2D.Float(0f, 0f, width, height)
    }

    val cgn = getCanvasGraphicsNode(gvtRoot)
    if (cgn == null) {
      curTxf = preserveAspectRatioMatrix
    }
    else {
      cgn.viewingTransform = preserveAspectRatioMatrix
      curTxf = AffineTransform()
    }
    this.root = gvtRoot
  }

  override fun setImageSize(docWidth: Float, docHeight: Float) {
    origDocWidth = docWidth
    origDocHeight = docHeight
    super.setImageSize(docWidth * scale, docHeight * scale)
  }

  override fun createUserAgent(): UserAgent {
    return object : SVGAbstractTranscoder.SVGAbstractTranscoderUserAgent() {
      override fun displayMessage(message: String?) {
        logger().debug(message)
      }

      override fun displayError(message: String?) {
        logger().debug(message)
      }

      override fun displayError(e: Exception) {
        logger().debug(e)
      }

      override fun getBrokenLinkDocument(e: Element, url: String, message: String): SVGDocument {
        logger().warn("$url $message")
        try {
          val fallbackIcon = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\">\n" +
                             "  <rect x=\"1\" y=\"1\" width=\"14\" height=\"14\" fill=\"none\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                             "  <line x1=\"1\" y1=\"1\" x2=\"15\" y2=\"15\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                             "  <line x1=\"1\" y1=\"15\" x2=\"15\" y2=\"1\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                             "</svg>\n"
          return createSvgDocument(null, StringReader(fallbackIcon)) as SVGDocument
        }
        catch (e: IOException) {
          throw IllegalStateException(e)
        }
      }

      override fun getScriptSecurity(scriptType: String?, scriptPURL: ParsedURL?, docPURL: ParsedURL?): ScriptSecurity {
        return NoLoadScriptSecurity(scriptType)
      }

      private fun logger() = Logger.getInstance(MyTranscoder::class.java)
    }
  }

  override fun transcode(input: TranscoderInput, output: TranscoderOutput) {
    throw IllegalStateException("must be not called")
  }

  // make it accessible
  public override fun createBridgeContext(doc: SVGOMDocument): BridgeContext {
    return if (doc.isSVG12) {
      SVG12BridgeContext(userAgent)
    }
    else {
      BridgeContext(userAgent)
    }
  }
}