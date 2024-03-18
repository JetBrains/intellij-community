// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.icons.findIconByPath
import com.intellij.ui.icons.getReflectiveIcon
import com.intellij.util.ui.GrayFilter
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import javax.swing.UIDefaults
import javax.swing.plaf.BorderUIResource

internal fun parseUiThemeValue(key: String, value: Any?, classLoader: ClassLoader, warn: (String, Throwable?) -> Unit): Any? {
  if (value !is String) {
    return value
  }

  try {
    val baseKey = key.removeSuffix(".compact")
    return when {
      value.endsWith(".png") || value.endsWith(".svg") -> parseImageFile(value, classLoader)
      baseKey.endsWith("Border") || baseKey.endsWith("border") -> parseBorder(value, classLoader)
      // not a part of parseStringValue - doesn't make sense, the value must be specified as number in JSON
      baseKey.endsWith("Width") || baseKey.endsWith("Height") -> getIntegerOrFloat(value = value, key = key)
      value.startsWith("AllIcons.") -> UIDefaults.LazyValue { getReflectiveIcon(value, classLoader) }
      // do not try to parse as number values that definitely should be a UI class name
      baseKey.endsWith("UI") -> value
      else -> {
        // ShowUIDefaultsContent can call parseUiThemeValue directly, that's why value maybe not yet parsed
        parseStringValue(value = value, key = key, warn = warn).let {
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

private fun parseBorder(value: String, classLoader: ClassLoader?): Any? {
  try {
    val parsedValues = parseMultiValue(value).iterator()
    val v1 = parsedValues.next()
    if (parsedValues.hasNext()) {
      val v2 = parsedValues.next()
      val v3 = parsedValues.next()
      val v4 = parsedValues.next()
      if (parsedValues.hasNext()) {
        return JBUI.asUIResource(JBUI.Borders.customLine(ColorHexUtil.fromHex(parsedValues.next()),
                                                         v1.toInt(),
                                                         v2.toInt(),
                                                         v3.toInt(),
                                                         v4.toInt()))
      }
      else {
        return BorderUIResource.EmptyBorderUIResource(JBInsets(v1.toInt(), v2.toInt(), v3.toInt(), v4.toInt()).asUIResource())
      }
    }
    else {
      return if (classLoader == null) value else parseBorderColorOrBorderClass(value, classLoader)
    }
  }
  catch (e: Exception) {
    logger<UITheme>().warn(e)
    return null
  }
}

private val LOOKUP = MethodHandles.lookup()

private fun parseBorderColorOrBorderClass(value: String, classLoader: ClassLoader): Any? {
  val color = parseColorOrNull(value = value, key = null)
  if (color == null) {
    return UIDefaults.LazyValue {
      val aClass = classLoader.loadClass(value)
      val constructor = MethodHandles.privateLookupIn(aClass, LOOKUP).findConstructor(aClass, MethodType.methodType(Void.TYPE))
      constructor.invoke()
    }
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

internal fun createColorResource(color: Color, key: String): Color {
  if (key.startsWith("*.")) {
    return color
  }
  else {
    return IJColorUIResource(color, key)
  }
}

internal fun parseStringValue(value: String, key: String, warn: (String, Throwable?) -> Unit): Any? {
  try {
    val baseKey = key.removeSuffix(".compact")
    return when {
      baseKey.endsWith("Insets") || baseKey.endsWith(".insets") || baseKey.endsWith("padding") -> parseInsets(value)
      baseKey.endsWith("Size") || baseKey.endsWith(".size") -> parseSize(value)
      baseKey.endsWith("Border") || baseKey.endsWith("border") -> parseBorder(value, classLoader = null)
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
      baseKey.endsWith("grayFilter") -> {
        val numbers = parseMultiValue(value).iterator()
        GrayFilter.asUIResource(numbers.next().toInt(), numbers.next().toInt(), numbers.next().toInt())
      }
      else -> {
        value
      }
    }
  }
  catch (e: Throwable) {
    warn("Cannot parse $value for $key", e)
    return null
  }
}