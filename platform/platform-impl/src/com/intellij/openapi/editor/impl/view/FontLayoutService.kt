// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.getPrivateMethod
import org.jetbrains.annotations.TestOnly
import sun.font.FontDesignMetrics
import java.awt.Font
import java.awt.FontMetrics
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodType

/**
 * Encapsulates logic related to font metrics.
 * A Mock instance can be used in tests to make them independent on font properties on a particular platform.
 */
abstract class FontLayoutService {
  abstract fun layoutGlyphVector(font: Font,
                                 fontRenderContext: FontRenderContext,
                                 chars: CharArray,
                                 start: Int,
                                 end: Int,
                                 isRtl: Boolean): GlyphVector

  abstract fun charWidth(fontMetrics: FontMetrics, c: Char): Int

  abstract fun charWidth(fontMetrics: FontMetrics, codePoint: Int): Int

  abstract fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float

  abstract fun stringWidth(fontMetrics: FontMetrics, str: String): Int

  abstract fun getHeight(fontMetrics: FontMetrics): Int

  abstract fun getDescent(fontMetrics: FontMetrics): Int

  companion object {
    @JvmStatic
    fun getInstance(): FontLayoutService = INSTANCE

    @TestOnly
    @JvmStatic
    fun setInstance(fontLayoutService: FontLayoutService?) {
      INSTANCE = fontLayoutService ?: DEFAULT_INSTANCE
    }
  }
}

private val DEFAULT_INSTANCE: FontLayoutService = DefaultFontLayoutService()
private var INSTANCE = DEFAULT_INSTANCE
private val LOG = Logger.getInstance(FontLayoutService::class.java)

private class DefaultFontLayoutService : FontLayoutService() {
  private val handleCharWidthMethod: MethodHandle? = try {
    FontDesignMetrics::class.java.getPrivateMethod(name = "handleCharWidth",
                                                   type = MethodType.methodType(java.lang.Float.TYPE, Integer.TYPE))
  }
  catch (e: Throwable) {
    LOG.warn("Couldn't access FontDesignMetrics.handleCharWidth method", e)
    null
  }

  private val getLatinCharWidthMethod: MethodHandle? = try {
    FontDesignMetrics::class.java.getPrivateMethod(name = "getLatinCharWidth",
                                                   type = MethodType.methodType(java.lang.Float.TYPE, Character.TYPE))
  }
  catch (e: Throwable) {
    LOG.warn("Couldn't access FontDesignMetrics.getLatinCharWidth method", e)
    null
  }

  override fun layoutGlyphVector(font: Font,
                                 fontRenderContext: FontRenderContext,
                                 chars: CharArray,
                                 start: Int,
                                 end: Int,
                                 isRtl: Boolean): GlyphVector {
    return font.layoutGlyphVector(/* frc = */ fontRenderContext,
                                  /* text = */ chars,
                                  /* start = */ start,
                                  /* limit = */ end,
                                  /* flags = */ if (isRtl) Font.LAYOUT_RIGHT_TO_LEFT else Font.LAYOUT_LEFT_TO_RIGHT)
  }

  override fun charWidth(fontMetrics: FontMetrics, c: Char): Int = fontMetrics.charWidth(c)

  override fun charWidth(fontMetrics: FontMetrics, codePoint: Int): Int = fontMetrics.charWidth(codePoint)

  override fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float {
    // variable must be typed as FontDesignMetrics for invokeExact using
    val fontDesignMetrics = fontMetrics as? FontDesignMetrics
    if (fontDesignMetrics != null) {
      if (codePoint < 256 && getLatinCharWidthMethod != null) {
        try {
          return getLatinCharWidthMethod.invokeExact(fontDesignMetrics, codePoint.toChar()) as Float
        }
        catch (e: Throwable) {
          LOG.debug(e)
        }
      }
      if (handleCharWidthMethod != null) {
        try {
          return handleCharWidthMethod.invokeExact(fontDesignMetrics, codePoint) as Float
        }
        catch (e: Throwable) {
          LOG.debug(e)
        }
      }
    }
    return charWidth(fontMetrics, codePoint).toFloat()
  }

  override fun stringWidth(fontMetrics: FontMetrics, str: String): Int = fontMetrics.stringWidth(str)

  override fun getHeight(fontMetrics: FontMetrics): Int = fontMetrics.height

  override fun getDescent(fontMetrics: FontMetrics): Int = fontMetrics.descent
}