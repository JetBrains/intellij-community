// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.ui.JBColor
import com.intellij.vcs.log.paint.ColorGenerator
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.util.function.IntFunction
import javax.swing.UIManager
import kotlin.math.abs

/**
 * @author erokhins
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class DefaultColorGenerator(coroutineScope: CoroutineScope) : ColorGenerator {
  private val colorMap = Int2ObjectOpenHashMap<JBColor>().also {
    putDefaultColor(it)
  }

  init {
    ApplicationManager.getApplication().messageBus.connect(coroutineScope).subscribe(LafManagerListener.TOPIC, LafManagerListener {
      colorMap.clear()
      putDefaultColor(colorMap)
    })
  }

  override fun getColor(colorId: Int): JBColor {
    return colorMap.computeIfAbsent(colorId, IntFunction { calcColor(it) })
  }

  companion object {
    private val saturation get() = namedFloat("VersionControl.Log.Graph.saturation", 0.4f).coerceIn(0f, 1f)
    private val brightness get() = namedFloat("VersionControl.Log.Graph.brightness", 0.65f).coerceIn(0f, 1f)
    private val buffer = FloatArray(3)

    private fun calcColor(colorId: Int): JBColor {
      val r = colorId * 200 + 30
      val g = colorId * 130 + 50
      val b = colorId * 90 + 100
      return try {
        val hsb = Color.RGBtoHSB(rangeFix(r), rangeFix(g), rangeFix(b), buffer)
        val rgb = Color.HSBtoRGB(hsb[0], saturation, brightness)
        val color = Color(rgb)
        JBColor(color, color)
      }
      catch (a: IllegalArgumentException) {
        throw IllegalArgumentException("Color: $colorId ${r % 256} ${g % 256} ${b % 256}")
      }
    }

    private fun rangeFix(n: Int) = abs(n % 100) + 70

    private fun putDefaultColor(map: Int2ObjectOpenHashMap<JBColor>) {
      map.put(GraphColorManagerImpl.DEFAULT_COLOR, JBColor.BLACK)
    }

    private fun namedFloat(name: String, default: Float): Float {
      return when (val value = UIManager.get(name)) {
        is Float -> value
        is Double -> value.toFloat()
        is String -> value.toFloatOrNull() ?: default
        else -> default
      }
    }
  }
}