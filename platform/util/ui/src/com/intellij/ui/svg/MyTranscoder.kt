// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.svg

import com.intellij.openapi.diagnostic.Logger
import org.apache.batik.anim.dom.SAXSVGDocumentFactory
import org.apache.batik.anim.dom.SVGOMDocument
import org.apache.batik.bridge.BridgeContext
import org.apache.batik.bridge.UserAgent
import org.apache.batik.gvt.renderer.ImageRenderer
import org.apache.batik.transcoder.SVGAbstractTranscoder
import org.apache.batik.transcoder.TranscoderException
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder
import org.apache.batik.util.XMLResourceDescriptor
import org.w3c.dom.Element
import org.w3c.dom.svg.SVGDocument
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.StringReader
import java.lang.Exception

internal class MyTranscoder(private val scale: Double) : ImageTranscoder() {
  companion object {
    @JvmStatic
    val iconMaxSize: Double by lazy {
      var maxSize = Integer.MAX_VALUE.toDouble()
      if (!GraphicsEnvironment.isHeadless()) {
        val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val bounds = device.defaultConfiguration.bounds
        val tx = device.defaultConfiguration.defaultTransform
        maxSize = Math.max(bounds.width * tx.scaleX, bounds.height * tx.scaleY).toInt().toDouble()
      }
      maxSize
    }

    @Throws(TranscoderException::class)
    @JvmStatic
    @JvmOverloads
    fun createImage(scale: Double, input: TranscoderInput, overriddenWidth: Float = -1f, overriddenHeight: Float = -1f): MyTranscoder {
      val transcoder = MyTranscoder(scale)
      if (overriddenWidth != -1f) {
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, overriddenWidth)
      }
      if (overriddenHeight != -1f) {
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, overriddenHeight)
      }

      val iconMaxSize = iconMaxSize.toFloat()
      transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_MAX_WIDTH, iconMaxSize)
      transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_MAX_HEIGHT, iconMaxSize)
      transcoder.transcode(input, null)
      return transcoder
    }
  }
  var origDocWidth = 0f
  var origDocHeight = 0f
  var image: BufferedImage? = null
    private set

  init {
    width = 16f
    height = 16f
  }

  override fun setImageSize(docWidth: Float, docHeight: Float) {
    origDocWidth = docWidth
    origDocHeight = docHeight
    super.setImageSize((docWidth * scale).toFloat(), (docHeight * scale).toFloat())
  }

  override fun createImage(w: Int, h: Int): BufferedImage {
    @Suppress("UndesirableClassUsage")
    return BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
  }

  override fun writeImage(image: BufferedImage, output: TranscoderOutput?) {
    this.image = image
  }

  override fun createRenderer(): ImageRenderer {
    val r = super.createRenderer()
    val rh = r.renderingHints
    rh.add(RenderingHints(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE))
    r.renderingHints = rh
    return r
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
        return createFallbackPlaceholder()
      }

      private fun logger() = Logger.getInstance(MyTranscoder::class.java)
    }
  }

  // make it accessible
  public override fun createBridgeContext(doc: SVGOMDocument): BridgeContext {
    return super.createBridgeContext(doc)
  }
}

private fun createFallbackPlaceholder(): SVGDocument {
  try {
    val fallbackIcon = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\">\n" +
                       "  <rect x=\"1\" y=\"1\" width=\"14\" height=\"14\" fill=\"none\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                       "  <line x1=\"1\" y1=\"1\" x2=\"15\" y2=\"15\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                       "  <line x1=\"1\" y1=\"15\" x2=\"15\" y2=\"1\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                       "</svg>\n"

    val factory = SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName())
    return factory.createDocument(null, StringReader(fallbackIcon)) as SVGDocument
  }
  catch (e: IOException) {
    throw IllegalStateException(e)
  }
}