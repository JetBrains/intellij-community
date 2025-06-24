/*
 * Copyright (C) 2019 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("UseJBColor")

package com.intellij.compose.ide.plugin.shared

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.impl.LineMarkersPass
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.ui.colorpicker.ColorPickerBuilder
import com.intellij.ui.colorpicker.LightCalloutPopup
import com.intellij.ui.colorpicker.MaterialGraphicalColorPipetteProvider
import com.intellij.ui.picker.ColorListener
import com.intellij.util.ui.ColorIcon
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.parsing.parseNumericLiteral
import org.jetbrains.kotlin.psi.*
import java.awt.Color
import java.awt.MouseInfo
import java.awt.event.MouseEvent
import java.util.*

private const val ICON_SIZE = 8

@ApiStatus.Internal
abstract class ComposeColorLineMarkerProviderDescriptor : LineMarkerProviderDescriptor() {
  override fun getName(): String = ComposeIdeBundle.message("compose.color.picker.name")

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    if (element.elementType != KtTokens.IDENTIFIER) return null

    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
    if (!isComposeEnabledInModule(module)) return null

    // don't provide line markers for the places where Android Jetpack Compose plugin is supposed to work
    // i.e., Android modules with an available Android Facet
    if (hasAndroidComposeColorLineMarkerProviderDescriptorAvailable(element.project) && isAndroidModule(module)) return null

    val callExpression = element.parent.parent as? KtCallExpression ?: return null
    if (!callExpression.isColorCall()) return null

    val color = getColor(callExpression) ?: return null
    val iconRenderer = ColorIconRenderer(callExpression, color)
    return LineMarkerInfo(
      element,
      element.textRange,
      iconRenderer.icon,
      { ComposeIdeBundle.message("compose.color.picker.tooltip") },
      iconRenderer,
      GutterIconRenderer.Alignment.RIGHT,
      { ComposeIdeBundle.message("compose.color.picker.tooltip") },
    )
  }

  private fun KtCallExpression.isColorCall() =
    COLOR_METHOD == (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() &&
    // Resolve the MethodCall expression after the faster checks
    run {
      val returnTypeFqName = callReturnTypeFqName()
      returnTypeFqName?.asString() == COMPOSE_COLOR_CLASS
    }

  private fun getColor(callExpression: KtCallExpression): Color? {
    val arguments = callExpression.valueArguments
    return when (getConstructorType(callExpression.valueArguments)) {
      ComposeColorConstructor.INT -> getColorInt(arguments)
      ComposeColorConstructor.LONG -> getColorLong(arguments)
      ComposeColorConstructor.INT_X3 -> getColorIntX3(arguments)
      ComposeColorConstructor.INT_X4 -> getColorIntX4(arguments)
      ComposeColorConstructor.FLOAT_X3 -> getColorFloatX3(arguments)
      ComposeColorConstructor.FLOAT_X4 -> getColorFloatX4(arguments)
      // TODO: Provide the color preview for ComposeColorConstructor.FLOAT_X4_COLORSPACE
      // constructor.
      ComposeColorConstructor.FLOAT_X4_COLORSPACE -> null
      else -> null
    }
  }

  private fun getColorInt(arguments: List<KtValueArgument>): Color? {
    val colorValue =
      arguments.first().getArgumentExpression()?.evaluateToConstantOrNull<Int>() ?: return null
    return Color(colorValue, true)
  }

  private fun getColorLong(arguments: List<KtValueArgument>): Color? {
    val colorValue =
      arguments.first().getArgumentExpression()?.evaluateToConstantOrNull<Long>() ?: return null
    return Color(colorValue.toInt(), true)
  }

  private fun getColorIntX3(arguments: List<KtValueArgument>): Color? {
    val rgbValues = getNamedValues<Int>(ARGS_RGB, arguments) ?: return null
    return intColorMapToColor(rgbValues)
  }

  private fun getColorIntX4(arguments: List<KtValueArgument>): Color? {
    val rgbaValues = getNamedValues<Int>(ARGS_RGBA, arguments) ?: return null
    return intColorMapToColor(rgbaValues)
  }

  private fun getColorFloatX3(arguments: List<KtValueArgument>): Color? {
    val rgbValues = getNamedValues<Float>(ARGS_RGB, arguments) ?: return null
    return floatColorMapToColor(rgbValues)
  }

  private fun getColorFloatX4(arguments: List<KtValueArgument>): Color? {
    val rgbaValues = getNamedValues<Float>(ARGS_RGBA, arguments) ?: return null
    return floatColorMapToColor(rgbaValues)
  }

  /**
   * This function return the name-value pair for the request arguments names by extracting the given
   * ktValueArguments.
   */
  private inline fun <reified T> getNamedValues(
    requestArgumentNames: List<String>,
    ktValueArgument: List<KtValueArgument>,
  ): Map<String, T>? {
    val namedValues = mutableMapOf<String, T>()

    val unnamedValue = mutableListOf<T>()
    for (argument in ktValueArgument) {
      val (name, value) = getArgumentNameValuePair<T>(argument) ?: return null
      if (name != null) {
        namedValues[name] = value
      }
      else {
        unnamedValue.add(value)
      }
    }

    val unnamedArgument = requestArgumentNames.filterNot { it in namedValues.keys }.toList()
    if (unnamedArgument.size != unnamedValue.size) {
      // The number of argument values doesn't match the given KtValueArgument.
      return null
    }

    for (index in unnamedArgument.indices) {
      // Fill the unnamed argument value from KtValueArgument.
      namedValues[unnamedArgument[index]] = unnamedValue[index]
    }
    if (namedValues.keys != requestArgumentNames.toSet()) {
      // Has the redundant or missed argument(s).
      return null
    }
    return namedValues
  }

  private inline fun <reified T> getArgumentNameValuePair(
    valueArgument: KtValueArgument,
  ): Pair<String?, T>? {
    val name = valueArgument.getArgumentName()?.asName?.asString()
    val value = valueArgument.getArgumentExpression()?.evaluateToConstantOrNull<T>() ?: return null
    return name to value
  }

  private inline fun <reified T> KtExpression.evaluateToConstantOrNull(): T? {
    return evaluateToConstantOrNullImpl() as? T
  }

  protected abstract fun KtExpression.evaluateToConstantOrNullImpl(): Any?
}

/**
 * Simplified version of [AndroidAnnotatorUtil.ColorRenderer] that does not work on
 * [com.android.ide.common.rendering.api.ResourceReference] but still displays the same color picker.
 *
 * TODO(lukeegan): Implement for ComposeColorConstructor.FLOAT_X4_COLORSPACE Color parameter
 */
data class ColorIconRenderer(val ktCallExpression: KtCallExpression, val color: Color) :
  GutterIconNavigationHandler<PsiElement> {

  val icon: ColorIcon = ColorIcon(ICON_SIZE, color)

  override fun navigate(e: MouseEvent?, elt: PsiElement?) {
    val project = ktCallExpression.project
    val setColorTask: (Color) -> Unit = getSetColorTask() ?: return

    val pickerListener = ColorListener { color, _ ->
      ApplicationManager.getApplication()
        .invokeLater(
          {
            WriteCommandAction.runWriteCommandAction(
              project,
              ComposeIdeBundle.message("compose.color.picker.action.name"),
              null,
              { setColorTask.invoke(color) },
            )
          },
          project.disposed,
        )
    }

    val colorPicker =
      ColorPickerBuilder(showAlpha = true, showAlphaAsPercent = false)
        .setOriginalColor(color)
        .addSaturationBrightnessComponent()
        .addColorAdjustPanel(MaterialGraphicalColorPipetteProvider())
        .addColorValuePanel()
        .withFocus()
        .addColorListener(pickerListener)
        .focusWhenDisplay(true)
        .setFocusCycleRoot(true)
        .build()
    val dialog = LightCalloutPopup(colorPicker.content)
    dialog.show(location = MouseInfo.getPointerInfo().location)
  }

  @VisibleForTesting
  fun getSetColorTask(): ((Color) -> Unit)? {
    val constructorType = getConstructorType(ktCallExpression.valueArguments) ?: return null
    // No matter what the original format is, we make the format become one of:
    // - (0xAARRGGBB)
    // - (color = 0xAARRGGBB)
    // or
    // - ([0..255], [0..255], [0..255], [0.255])
    // - (red = [0..255], green = [0..255], blue = [0..255], alpha = [0.255])
    // or
    // - ([0x00..0xFF], [0x00..0xFF], [0x00..0xFF], [0x00..0xFF])
    // - (red = [0x00..0xFF], green =[0x00..0xFF], blue = [0x00..0xFF], alpha = [0x00..0xFF])
    // or
    // - ([0.0f..1.0f], [0.0f..1.0f], [0.0f..1.0f], [0.0f..1.0f])
    // - (red = [0.0f..1.0f], green = [0.0f..1.0f], blue = [0.0f..1.0f], alpha = [0.0f..1.0f])
    // , depends on the original value type and numeral system.
    return when (constructorType) {
      ComposeColorConstructor.INT,
      ComposeColorConstructor.LONG,
        -> { color: Color ->
        val valueArgumentList = ktCallExpression.valueArgumentList
        if (valueArgumentList != null) {
          val needsArgumentName = valueArgumentList.arguments.any { it.getArgumentName() != null }
          val hexString = "0x${String.format("%08X", color.rgb)}"
          val argumentText = if (needsArgumentName) "(color = $hexString)" else "($hexString)"
          valueArgumentList.replace(
            KtPsiFactory(ktCallExpression.project).createCallArguments(argumentText)
          )
        }
      }
      ComposeColorConstructor.INT_X3,
      ComposeColorConstructor.INT_X4,
        -> { color: Color ->
        val valueArgumentList = ktCallExpression.valueArgumentList
        if (valueArgumentList != null) {
          val needsArgumentName = valueArgumentList.arguments.any { it.getArgumentName() != null }
          val hasHexFormat =
            valueArgumentList.arguments.any {
              it.getArgumentExpression()?.text?.startsWith("0x") ?: false
            }
          val red = if (hasHexFormat) color.red.toHexString() else color.red.toString()
          val green = if (hasHexFormat) color.green.toHexString() else color.green.toString()
          val blue = if (hasHexFormat) color.blue.toHexString() else color.blue.toString()
          val alpha = if (hasHexFormat) color.alpha.toHexString() else color.alpha.toString()

          val argumentText =
            if (needsArgumentName) "(red = $red, green = $green, blue = $blue, alpha = $alpha)"
            else "($red, $green, $blue, $alpha)"
          valueArgumentList.replace(
            KtPsiFactory(ktCallExpression.project).createCallArguments(argumentText)
          )
        }
      }
      ComposeColorConstructor.FLOAT_X3,
      ComposeColorConstructor.FLOAT_X4,
        -> { color: Color ->
        val valueArgumentList = ktCallExpression.valueArgumentList
        if (valueArgumentList != null) {
          val needsArgumentName = valueArgumentList.arguments.any { it.getArgumentName() != null }
          val red = (color.red / 255f).toRoundString()
          val green = (color.green / 255f).toRoundString()
          val blue = (color.blue / 255f).toRoundString()
          val alpha = (color.alpha / 255f).toRoundString()

          val argumentText =
            if (needsArgumentName)
              "(red = ${red}f, green = ${green}f, blue = ${blue}f, alpha = ${alpha}f)"
            else "(${red}f, ${green}f, ${blue}f, ${alpha}f)"
          valueArgumentList.replace(
            KtPsiFactory(ktCallExpression.project).createCallArguments(argumentText)
          )
        }
      }
      ComposeColorConstructor.FLOAT_X4_COLORSPACE ->
        null // TODO: support ComposeColorConstructor.FLOAT_X4_COLORSPACE in the future.
    }
  }
}

private const val COLOR_METHOD = "Color"
private const val COMPOSE_COLOR_CLASS = "androidx.compose.ui.graphics.Color"

private const val ARG_NAME_RED = "red"
private const val ARG_NAME_GREEN = "green"
private const val ARG_NAME_BLUE = "blue"
private const val ARG_NAME_ALPHA = "alpha"

private val ARGS_RGB = listOf(ARG_NAME_RED, ARG_NAME_GREEN, ARG_NAME_BLUE)
private val ARGS_RGBA = listOf(ARG_NAME_RED, ARG_NAME_GREEN, ARG_NAME_BLUE, ARG_NAME_ALPHA)

enum class ComposeColorConstructor {
  INT,
  LONG,
  INT_X3,
  INT_X4,
  FLOAT_X3,
  FLOAT_X4,
  FLOAT_X4_COLORSPACE,
}

private fun Int.toHexString(): String =
  "0x${(Integer.toHexString(this)).uppercase(Locale.getDefault())}"

// Note: toFloat() then toString() is for removing the tail zero(s).
private fun Float.toRoundString(decimals: Int = 3): String =
  "%.${decimals}f".format(this).toFloat().toString()

private typealias IntColorMap = Map<String, Int>

private fun intColorMapToColor(intColorMap: IntColorMap): Color? {
  val red = intColorMap[ARG_NAME_RED] ?: return null
  val green = intColorMap[ARG_NAME_GREEN] ?: return null
  val blue = intColorMap[ARG_NAME_BLUE] ?: return null
  val alpha = intColorMap[ARG_NAME_ALPHA]
  return if (alpha == null) Color(red, green, blue) else Color(red, green, blue, alpha)
}

private typealias FloatColorMap = Map<String, Float>

private fun floatColorMapToColor(floatColorMap: FloatColorMap): Color? {
  val red = floatColorMap[ARG_NAME_RED] ?: return null
  val green = floatColorMap[ARG_NAME_GREEN] ?: return null
  val blue = floatColorMap[ARG_NAME_BLUE] ?: return null
  val alpha = floatColorMap[ARG_NAME_ALPHA]
  return if (alpha == null) Color(red, green, blue) else Color(red, green, blue, alpha)
}

private enum class ComposeColorParamType {
  FLOAT, INT, LONG
}

private fun KtConstantExpression.colorParamTypeOrNull(): ComposeColorParamType? {
  val type = node.elementType
  if (type == KtNodeTypes.FLOAT_CONSTANT) return ComposeColorParamType.FLOAT
  if (type != KtNodeTypes.INTEGER_CONSTANT) return null
  if (text.endsWith("L")) return ComposeColorParamType.LONG

  val numericalValue = parseNumericLiteral(text, type)
  return when {
    numericalValue !is Long -> ComposeColorParamType.INT
    numericalValue > Integer.MAX_VALUE -> ComposeColorParamType.LONG
    numericalValue < Integer.MIN_VALUE -> ComposeColorParamType.LONG
    else -> ComposeColorParamType.INT
  }
}

private fun List<KtValueArgument>.singleColorParamTypeOrNull(): ComposeColorParamType? {
  val elementsTypes = map { (it.getArgumentExpression() as? KtConstantExpression)?.colorParamTypeOrNull() }
  return when {
    elementsTypes.all { it == ComposeColorParamType.FLOAT } -> ComposeColorParamType.FLOAT
    elementsTypes.all { it == ComposeColorParamType.INT } -> ComposeColorParamType.INT
    elementsTypes.all { it == ComposeColorParamType.LONG } -> ComposeColorParamType.LONG
    else -> null
  }
}

private fun getConstructorType(arguments: List<KtValueArgument>): ComposeColorConstructor? {
  val paramType = arguments.singleColorParamTypeOrNull() ?: return null
  return when (arguments.size) {
    1 -> when (paramType) {
      ComposeColorParamType.INT -> ComposeColorConstructor.INT
      ComposeColorParamType.LONG -> ComposeColorConstructor.LONG
      else -> null
    }
    3 -> when (paramType) {
      ComposeColorParamType.INT -> ComposeColorConstructor.INT_X3
      ComposeColorParamType.FLOAT -> ComposeColorConstructor.FLOAT_X3
      else -> null
    }
    4 -> when (paramType) {
      ComposeColorParamType.INT -> ComposeColorConstructor.INT_X4
      ComposeColorParamType.FLOAT -> ComposeColorConstructor.FLOAT_X4
      else -> null
    }
    5 -> ComposeColorConstructor.FLOAT_X4_COLORSPACE
    else -> null
  }
}

private fun hasAndroidComposeColorLineMarkerProviderDescriptorAvailable(project: Project): Boolean =
  LineMarkersPass.getMarkerProviders(KotlinLanguage.INSTANCE, project)
    .any { it::class.java.name == "com.android.tools.compose.ComposeColorLineMarkerProviderDescriptor" }