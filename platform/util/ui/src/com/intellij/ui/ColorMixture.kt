// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.SystemProperties
import com.intellij.util.ui.ComparableColor
import com.intellij.util.ui.PresentableColor
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.util.*
import java.util.function.Supplier

@ApiStatus.Internal
abstract class ColorMixture internal constructor(
  val name: @NlsSafe String,
) : PresentableColor, ComparableColor, Supplier<Color> {
  abstract val args: List<Any>

  override fun getPresentableName(): @NlsSafe String {
    return "$name: (${
      args.joinToString { argument ->
        when (argument) {
          is Color -> PresentableColor.toPresentableString(argument)
          else -> argument.toString()
        }
      }
    })"
  }

  override fun colorEquals(other: ComparableColor): Boolean {
    if (javaClass != other.javaClass) return false
    other as ColorMixture

    if (name != other.name) return false
    if (args.size != other.args.size) return false

    for (i in args.indices) {
      val argument1 = args[i]
      val argument2 = other.args[i]
      val argumentsEqual = when {
        argument1 is Color && argument2 is Color -> UIUtil.equalColors(argument1, argument2)
        argument1 is Color || argument2 is Color -> false
        else -> Objects.equals(argument1, argument2)
      }
      if (!argumentsEqual) return false
    }

    return true
  }

  override fun colorHashCode(): Int {
    var result = name.hashCode()
    for (argument in args) {
      val argumentHash = when (argument) {
        is Color -> UIUtil.colorHashCode(argument)
        else -> argument.hashCode()
      }
      result = 31 * result + argumentHash
    }
    return result
  }

  fun createColor(supportDynamicColors: Boolean): Color {
    if (supportDynamicColors && args.any { it is JBColor }) {
      return JBColor.lazy(this)
    }
    else if (SystemProperties.getBooleanProperty(ENABLE_RUNTIME_COLOR_MIXTURE_WRAPPER_OPTION, false)) {
      return NamedColor(getPresentableName(), get())
    }
    else {
      return get()
    }
  }

  companion object {
    @ApiStatus.Internal
    const val ENABLE_RUNTIME_COLOR_MIXTURE_WRAPPER_OPTION: String = "ide.color.mixture.mark.colors"
  }
}

@ApiStatus.Internal
class NamedColor internal constructor(val fullName: @NlsSafe String, color: Color) : ColorWrapper(color) {
  override fun getPresentableName(): @NlsSafe String = fullName

  override fun colorEquals(other: ComparableColor): Boolean {
    return other is NamedColor &&
           fullName == other.fullName &&
           this == other
  }

  override fun colorHashCode(): Int {
    return fullName.hashCode() + 31 * hashCode()
  }

  companion object {
    @JvmStatic
    fun namedColor(color: Color, name: @NlsSafe String): Color {
      return NamedColor(name, color)
    }
  }
}

@ApiStatus.Internal
class SwingTuneDarker(
  private val color: Color,
) : ColorMixture("swingDarker") {
  override val args: List<Any> get() = listOf(color)

  override fun get(): Color {
    return getRawColor(color).darker()
  }
}

@ApiStatus.Internal
class SwingTuneBrighter(
  private val color: Color,
) : ColorMixture("swingBrighter") {
  override val args: List<Any> get() = listOf(color)

  override fun get(): Color {
    return getRawColor(color).brighter()
  }
}

@Suppress("UseJBColor")
private fun getRawColor(color: Color): Color {
  // avoid recursion
  return Color(color.rgb, true)
}
