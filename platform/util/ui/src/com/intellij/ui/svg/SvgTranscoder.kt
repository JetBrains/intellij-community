// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UndesirableClassUsage")

package com.intellij.ui.svg

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ImageLoader
import org.apache.batik.anim.dom.SVGOMDocument
import org.apache.batik.bridge.*
import org.apache.batik.bridge.svg12.SVG12BridgeContext
import org.apache.batik.ext.awt.RenderingHintsKeyExt
import org.apache.batik.gvt.CanvasGraphicsNode
import org.apache.batik.gvt.CompositeGraphicsNode
import org.apache.batik.gvt.GraphicsNode
import org.apache.batik.transcoder.TranscoderException
import org.apache.batik.util.ParsedURL
import org.apache.batik.util.SVGConstants
import org.apache.batik.util.SVGFeatureStrings
import org.jetbrains.annotations.ApiStatus
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.svg.SVGAElement
import org.w3c.dom.svg.SVGDocument
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.max
import kotlin.math.min

private fun logger() = Logger.getInstance(SvgTranscoder::class.java)

private val identityTransform = AffineTransform()
private val supportedFeatures = HashSet<String>()

@ApiStatus.Internal
class SvgTranscoder private constructor(private var width: Float, private var height: Float) : UserAgent {
  companion object {
    // An SVG tag custom attribute, optional for @2x SVG icons.
    // When provided and is set to "true" the document size should be treated as double-scaled of the base size.
    // See https://youtrack.jetbrains.com/issue/IDEA-267073
    const val DATA_SCALED_ATTR = "data-scaled"

    init {
      SVGFeatureStrings.addSupportedFeatureStrings(supportedFeatures)
    }

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

    @JvmStatic
    fun getDocumentSize(scale: Float, document: Document): ImageLoader.Dimension2DDouble {
      val transcoder = SvgTranscoder(16f, 16f)
      val bridgeContext = if ((document as SVGOMDocument).isSVG12) {
        SVG12BridgeContext(transcoder)
      }
      else {
        BridgeContext(transcoder)
      }
      GVTBuilder().build(bridgeContext, document)
      val size = bridgeContext.documentSize
      return ImageLoader.Dimension2DDouble(size.width * scale, size.height * scale)
    }

    @Throws(TranscoderException::class)
    @JvmStatic
    @JvmOverloads
    fun createImage(scale: Float,
                    document: Document,
                    outDimensions: ImageLoader.Dimension2DDouble? /*OUT*/,
                    overriddenWidth: Float = -1f,
                    overriddenHeight: Float = -1f): BufferedImage {
      val transcoder = SvgTranscoder(if (overriddenWidth == -1f) 16f else overriddenWidth,
                                     if (overriddenHeight == -1f) 16f else overriddenHeight)

      val iconMaxSize = iconMaxSize
      val bridgeContext = if ((document as SVGOMDocument).isSVG12) {
        SVG12BridgeContext(transcoder)
      }
      else {
        BridgeContext(transcoder)
      }

      try {
        // build the GVT tree - it will set bridgeContext.documentSize
        val gvtRoot = GVTBuilder().build(bridgeContext, document)!!
        // get the 'width' and 'height' attributes of the SVG document
        val docWidth = bridgeContext.documentSize.width.toFloat()
        val docHeight = bridgeContext.documentSize.height.toFloat()

        var normalizingScale = 1f
        if ((document.url?.contains("@2x") == true) and
            document.rootElement?.attributes?.getNamedItem(DATA_SCALED_ATTR)?.nodeValue?.lowercase(Locale.ENGLISH).equals("true"))
        {
          normalizingScale = 2f
        }
        val imageScale = scale / normalizingScale
        transcoder.setImageSize(docWidth * imageScale, docHeight * imageScale, overriddenWidth, overriddenHeight, iconMaxSize)
        val transform = computeTransform(document, gvtRoot, bridgeContext, docWidth, docHeight, transcoder.width, transcoder.height)
        transcoder.currentTransform = transform

        val image = render((transcoder.width + 0.5f).toInt(), (transcoder.height + 0.5f).toInt(), transform, gvtRoot)

        // Take into account the image size rounding and correct the original user size in order to compensate the inaccuracy.
        val effectiveUserWidth = image.width / scale
        val effectiveUserHeight = image.height / scale

        // outDimensions should contain the base size
        outDimensions?.setSize(effectiveUserWidth.toDouble() / normalizingScale, effectiveUserHeight.toDouble() / normalizingScale)
        return image
      }
      catch (e: TranscoderException) {
        throw e
      }
      catch (e: Exception) {
        throw TranscoderException(e)
      }
      finally {
        bridgeContext.dispose()
      }
    }
  }

  private var currentTransform: AffineTransform? = null

  private fun setImageSize(docWidth: Float, docHeight: Float, overriddenWidth: Float, overriddenHeight: Float, iconMaxSize: Float) {
    if (overriddenWidth > 0 && overriddenHeight > 0) {
      width = overriddenWidth
      height = overriddenHeight
    }
    else if (overriddenHeight > 0) {
      width = docWidth * overriddenHeight / docHeight
      height = overriddenHeight
    }
    else if (overriddenWidth > 0) {
      width = overriddenWidth
      height = docHeight * overriddenWidth / docWidth
    }
    else {
      width = docWidth
      height = docHeight
    }

    // limit image size according to the maximum size hints
    if (iconMaxSize > 0 && height > iconMaxSize) {
      width = docWidth * iconMaxSize / docHeight
      height = iconMaxSize
    }
    if (iconMaxSize > 0 && width > iconMaxSize) {
      width = iconMaxSize
      height = docHeight * iconMaxSize / docWidth
    }
  }

  override fun getMedia() = "screen"

  override fun getBrokenLinkDocument(e: Element, url: String, message: String): SVGDocument {
    logger().warn("$url $message")
    val fallbackIcon = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\">\n" +
                       "  <rect x=\"1\" y=\"1\" width=\"14\" height=\"14\" fill=\"none\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                       "  <line x1=\"1\" y1=\"1\" x2=\"15\" y2=\"15\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                       "  <line x1=\"1\" y1=\"15\" x2=\"15\" y2=\"1\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                       "</svg>\n"
    return createSvgDocument(null, fallbackIcon.toByteArray()) as SVGDocument
  }

  override fun getTransform() = currentTransform!!

  override fun setTransform(value: AffineTransform) = throw IllegalStateException()

  override fun getViewportSize() = Dimension(width.toInt(), height.toInt())

  override fun displayError(e: Exception) {
    logger().debug(e)
  }

  override fun displayMessage(message: String) {
    logger().debug(message)
  }

  override fun getScriptSecurity(scriptType: String?, scriptUrl: ParsedURL?, documentUrl: ParsedURL?) = NoLoadScriptSecurity(scriptType)

  override fun getExternalResourceSecurity(resourceUrl: ParsedURL, documentUrl: ParsedURL?): ExternalResourceSecurity {
    return ExternalResourceSecurity { checkLoadExternalResource(resourceUrl, documentUrl) }
  }

  override fun showAlert(message: String?) {}

  override fun showPrompt(message: String?) = null

  override fun showPrompt(message: String?, defaultValue: String?) = null

  override fun showConfirm(message: String?) = false

  override fun getPixelUnitToMillimeter(): Float = 0.26458333333333333333333333333333f // 96dpi

  override fun getPixelToMM() = pixelUnitToMillimeter

  override fun getDefaultFontFamily() = "Arial, Helvetica, sans-serif"

  // 9pt (72pt = 1in)
  override fun getMediumFontSize(): Float = 9f * 25.4f / (72f * pixelUnitToMillimeter)

  override fun getLighterFontWeight(f: Float) = getStandardLighterFontWeight(f)

  override fun getBolderFontWeight(f: Float) = getStandardBolderFontWeight(f)

  override fun getLanguages() = "en"

  override fun getAlternateStyleSheet() = null

  override fun getUserStyleSheetURI() = null

  override fun getXMLParserClassName() = null

  override fun isXMLParserValidating() = false

  override fun getEventDispatcher() = null

  override fun openLink(elt: SVGAElement?) {}

  override fun setSVGCursor(cursor: Cursor?) {}

  override fun setTextSelection(start: Mark?, end: Mark?) {}

  override fun deselectAll() {}

  override fun getClientAreaLocationOnScreen() = Point()

  override fun hasFeature(s: String?) = supportedFeatures.contains(s)

  override fun supportExtension(s: String?) = false

  override fun registerExtension(ext: BridgeExtension) {
  }

  override fun handleElement(elt: Element?, data: Any?) {}

  override fun checkLoadScript(scriptType: String?, scriptURL: ParsedURL, docURL: ParsedURL?) {
    throw SecurityException("NO_EXTERNAL_RESOURCE_ALLOWED")
  }

  override fun checkLoadExternalResource(resourceUrl: ParsedURL, documentUrl: ParsedURL?) {
    // make sure that the archives comes from the same host as the document itself
    if (documentUrl == null) {
      throw SecurityException("NO_EXTERNAL_RESOURCE_ALLOWED")
    }

    val docHost = documentUrl.host
    val externalResourceHost: String = resourceUrl.host
    if (docHost != externalResourceHost && "data" != resourceUrl.protocol) {
      throw SecurityException("NO_EXTERNAL_RESOURCE_ALLOWED")
    }
  }

  override fun loadDocument(url: String?) {
  }

  override fun getFontFamilyResolver(): FontFamilyResolver = DefaultFontFamilyResolver.SINGLETON
}

private fun computeTransform(document: SVGOMDocument,
                             gvtRoot: GraphicsNode,
                             context: BridgeContext,
                             docWidth: Float,
                             docHeight: Float,
                             width: Float,
                             height: Float): AffineTransform {
  // compute the preserveAspectRatio matrix
  val preserveAspectRatioMatrix: AffineTransform
  val root = document.rootElement
  val viewBox = root.getAttributeNS(null, SVGConstants.SVG_VIEW_BOX_ATTRIBUTE)
  if (viewBox.isNotEmpty()) {
    val aspectRatio = root.getAttributeNS(null, SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE)
    preserveAspectRatioMatrix = ViewBox.getPreserveAspectRatioTransform(root, viewBox, aspectRatio, width, height, context)
  }
  else {
    // no viewBox has been specified, create a scale transform
    val scale = min(width / docWidth, height / docHeight)
    preserveAspectRatioMatrix = AffineTransform.getScaleInstance(scale.toDouble(), scale.toDouble())
  }

  val cgn = (gvtRoot as? CompositeGraphicsNode)?.children?.firstOrNull() as? CanvasGraphicsNode
  if (cgn == null) {
    return preserveAspectRatioMatrix
  }
  else {
    cgn.viewingTransform = preserveAspectRatioMatrix
    return AffineTransform()
  }
}

private fun render(offScreenWidth: Int, offScreenHeight: Int, usr2dev: AffineTransform, gvtRoot: GraphicsNode): BufferedImage {
  val image = BufferedImage(offScreenWidth, offScreenHeight, BufferedImage.TYPE_INT_ARGB)

  val g = image.createGraphics()
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
  g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
  g.setRenderingHint(RenderingHintsKeyExt.KEY_BUFFERED_IMAGE, WeakReference(image))
  g.transform = identityTransform
  @Suppress("GraphicsSetClipInspection")
  g.setClip(0, 0, offScreenWidth, offScreenHeight)
  g.composite = AlphaComposite.Clear
  g.fillRect(0, 0, offScreenWidth, offScreenHeight)
  g.composite = AlphaComposite.SrcOver
  g.transform(usr2dev)
  gvtRoot.paint(g)
  g.dispose()
  return image
}

private fun getStandardLighterFontWeight(f: Float): Float {
  // Round f to nearest 100...
  return when (((f + 50) / 100).toInt() * 100) {
    100, 200 -> 100f
    300 -> 200f
    400 -> 300f
    500, 600, 700, 800, 900 -> 400f
    else -> throw IllegalArgumentException("Bad Font Weight: $f")
  }
}

private fun getStandardBolderFontWeight(f: Float): Float {
  // Round f to nearest 100...
  return when (((f + 50) / 100).toInt() * 100) {
    100, 200, 300, 400, 500 -> 600f
    600 -> 700f
    700 -> 800f
    800 -> 900f
    900 -> 900f
    else -> throw IllegalArgumentException("Bad Font Weight: $f")
  }
}