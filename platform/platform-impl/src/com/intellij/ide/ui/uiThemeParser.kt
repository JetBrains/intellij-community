// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.ui.laf.IJColorUIResource
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.icons.ImageDataByPathLoader.Companion.findIconByPath
import com.intellij.ui.icons.getReflectiveIcon
import com.intellij.util.ui.GrayFilter
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import javax.swing.UIDefaults
import javax.swing.plaf.BorderUIResource
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.UIResource

internal fun parseUiThemeValue(key: String, value: Any?, classLoader: ClassLoader): Any? {
  if (value !is String) {
    return value
  }

  try {
    return when {
      value.endsWith(".png") || value.endsWith(".svg") -> parseImageFile(value, classLoader)
      key.endsWith("Border") || key.endsWith("border") -> parseBorder(value, classLoader)
      // not a part of parseStringValue - doesn't make sense, the value must be specified as number in JSON
      key.endsWith("Width") || key.endsWith("Height") -> getIntegerOrFloat(value = value, key = key)
      value.startsWith("AllIcons.") -> UIDefaults.LazyValue { getReflectiveIcon(value, classLoader) }
      // do not try to parse as number values that definitely should be a UI class name
      key.endsWith("UI") -> value
      else -> {
        // ShowUIDefaultsContent can call parseUiThemeValue directly, that's why value maybe not yet parsed
        parseStringValue(value = value, key = key).let {
          if (it !is String) {
            return it
          }
        }

        // key as null to log as warning
        getIntegerOrFloat(value, null)?.let {
          logger<UITheme>().warn("$key has numeric value but specified as string")
          return it
        }
        if (value.length <= 8) {
          parseColorOrNull(value, null)?.let {
            logger<UITheme>().warn("$key has color value but doesn't have # prefix")
            return it
          }
        }

        value
      }
    }
  }
  catch (e: Exception) {
    logger<UITheme>().warn("Can't parse '$value' for key '$key'")
    return value
  }
}

internal fun parseColorOrNull(value: String, key: String?): Color? {
  try {
    return ColorHexUtil.fromHexOrNull(value)
  }
  catch (e: Exception) {
    if (key != null) {
      logger<UITheme>().warn("$key=$value has # prefix but cannot be parsed as color")
    }
    return null
  }
}

private fun parseImageFile(value: String, classLoader: ClassLoader): Any {
  return UIDefaults.LazyValue { findIconByPath(path = value, classLoader = classLoader, cache = null, toolTip = null) }
}

private fun parseBorder(value: String, classLoader: ClassLoader): Any? {
  return try {
    val parsedValues = parseMultiValue(value).toList()
    when (parsedValues.size) {
      4 -> BorderUIResource.EmptyBorderUIResource(parseInsets(value))
      5 -> JBUI.asUIResource(JBUI.Borders.customLine(ColorHexUtil.fromHex(parsedValues[4]),
                                                     parsedValues[0].toInt(),
                                                     parsedValues[1].toInt(),
                                                     parsedValues[2].toInt(),
                                                     parsedValues[3].toInt()))
      else -> parseBorderColorOrBorderClass(value, classLoader)
    }
  }
  catch (e: Exception) {
    logger<UITheme>().warn(e)
    null
  }
}

private fun parseBorderColorOrBorderClass(value: String, classLoader: ClassLoader): Any? {
  val color = parseColorOrNull(value = value, key = null)
  if (color == null) {
    val aClass = classLoader.loadClass(value)
    val constructor = aClass.getDeclaredConstructor()
    constructor.isAccessible = true
    return constructor.newInstance()
  }
  else {
    return JBUI.asUIResource(JBUI.Borders.customLine(color, 1))
  }
}

private fun parseMultiValue(value: String) = value.splitToSequence(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun getIntegerOrFloat(value: String, key: String?): Number? {
  try {
    return if (value.contains('.')) value.toFloat() else value.toInt()
  }
  catch (e: NumberFormatException) {
    if (key != null) {
      logger<UITheme>().warn("Can't parse: $key = $value")
    }
    return null
  }
}

private fun parseSize(value: String): Dimension {
  val numbers = parseMultiValue(value).iterator()
  return JBDimension(numbers.next().toInt(), numbers.next().toInt()).asUIResource()
}

private fun parseInsets(value: String): Insets {
  val numbers = parseMultiValue(value).iterator()
  return JBInsets(numbers.next().toInt(), numbers.next().toInt(), numbers.next().toInt(), numbers.next().toInt()).asUIResource()
}

internal fun isColorLike(text: String) = text.length <= 9 && text.startsWith('#')

internal fun createColorResource(color: Color?, key: String): UIResource {
  if (key.startsWith("*.")) {
    return ColorUIResource(color)
  }
  else {
    return IJColorUIResource(color, key)
  }
}

internal fun parseStringValue(value: String, key: String): Any {
  return when {
    key.endsWith("Insets") || key.endsWith(".insets") || key.endsWith("padding") -> parseInsets(value)
    key.endsWith("Size") -> parseSize(value)
    isColorLike(value) -> {
      val color = parseColorOrNull(value, null)
      if (color == null) {
        logger<UITheme>().warn("$key=$value has # prefix but cannot be parsed as color")
        value
      }
      else {
        createColorResource(color, key)
      }
    }
    key.endsWith("Insets") || key.endsWith(".insets") || key.endsWith("padding") -> parseInsets(value)
    key.endsWith("grayFilter") -> {
      val numbers = parseMultiValue(value).iterator()
      GrayFilter.asUIResource(numbers.next().toInt(), numbers.next().toInt(), numbers.next().toInt())
    }
    else -> {
      value
    }
  }
}