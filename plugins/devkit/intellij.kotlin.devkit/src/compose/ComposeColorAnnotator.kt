// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.compose

import com.intellij.ide.IdeBundle
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.ui.ColorChooserService
import com.intellij.ui.ColorUtil
import com.intellij.ui.picker.ColorListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.ColorIcon
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.psi.*
import java.awt.Color
import java.lang.Float
import java.lang.Long
import java.util.*
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Exception
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.UnsupportedOperationException
import kotlin.toString

/**
 * @author Alexander Lobas
 */
internal class ComposeColorAnnotator : Annotator, DumbAware {
  private val names = listOf("red", "green", "blue", "alpha", "value", "color")

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val color = getColor(element)
    if (color != null) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .gutterIconRenderer(ColorRenderer(color, element as KtCallExpression, ::write)).create()
    }
  }

  @Suppress("UseJBColor")
  private fun getColor(element: PsiElement): Color? {
    if (element !is KtCallExpression) {
      return null
    }

    val calleeExpression = element.calleeExpression ?: return null

    if (calleeExpression.text != "Color") {
      return null
    }

    val valueArguments = element.valueArguments
    val arguments = valueArguments.size
    if (arguments != 1 && (arguments < 3 || arguments > 5)) {
      return null
    }

    valueArguments.forEach {
      val value = it.getArgumentExpression() ?: return null

      val name = it.getArgumentName()?.asName?.toString()
      if (name != null && !names.contains(name)) {
        return null
      }

      if (value !is KtConstantExpression) {
        return null
      }
      val elementType = value.node.elementType
      if (elementType != KtNodeTypes.INTEGER_CONSTANT && elementType != KtNodeTypes.FLOAT_CONSTANT) {
        return null
      }
    }

    val function = calleeExpression.reference?.resolve()
    if (function !is KtNamedFunction || function.fqName.toString() != "androidx.compose.ui.graphics.Color") {
      return null
    }

    if (arguments == 1) {
      val text = valueArguments[0].getArgumentExpression()!!.text
      try {
        return Color(Long.decode(text).toInt(), true)
      }
      catch (_: Exception) {
        return null
      }
    }

    if ((arguments == 3 || arguments == 4) &&
        valueArguments.all { it.getArgumentExpression()!!.node.elementType == KtNodeTypes.INTEGER_CONSTANT }
    ) {
      val colorComponents = getColorComponents(valueArguments, "0xFF") ?: return null

      try {
        return Color(Integer.decode(colorComponents[0]), Integer.decode(colorComponents[1]),
                     Integer.decode(colorComponents[2]), Integer.decode(colorComponents[3]))
      }
      catch (_: Exception) {
        return null
      }
    }

    val floatValues = valueArguments.count { it.getArgumentExpression()!!.node.elementType == KtNodeTypes.FLOAT_CONSTANT }
    if (floatValues == 3 || floatValues == 4) {
      val colorComponents = getColorComponents(valueArguments, "1f") ?: return null

      try {
        return Color(Float.parseFloat(colorComponents[0]), Float.parseFloat(colorComponents[1]),
                     Float.parseFloat(colorComponents[2]), Float.parseFloat(colorComponents[3]))
      }
      catch (_: Exception) {
        return null
      }
    }

    return null
  }

  private fun getColorComponents(arguments: List<KtValueArgument>, defaultAlpha: String): Array<String>? {
    val components = Array<String>(4) { "" }
    components[3] = defaultAlpha

    for ((index, argument) in arguments.withIndex()) {
      val text = argument.getArgumentExpression()!!.text
      val name = argument.getArgumentName()?.asName?.toString()

      if (name == null) {
        if (index >= components.size) {
          return null
        }
        components[index] = text
      }
      else when (name) {
        "red" -> components[0] = text

        "green" -> components[1] = text

        "blue" -> components[2] = text

        "alpha" -> components[3] = text

        else -> return null
      }
    }

    return components
  }

  private fun write(element: KtCallExpression, color: Color) {
    val psiFactory = KtPsiFactory(element.project)

    val valueArguments = element.valueArguments

    if (valueArguments.size == 1) {
      val newValue = "0x" + StringUtil.toUpperCase(Integer.toHexString(color.alpha)) + StringUtil.toUpperCase(ColorUtil.toHex(color))
      valueArguments[0].getArgumentExpression()!!.replace(psiFactory.createExpression(newValue))
    }
    else if (valueArguments[0].getArgumentExpression()!!.node.elementType == KtNodeTypes.INTEGER_CONSTANT) {
      for ((index, argument) in valueArguments.withIndex()) {
        val oldValue = Integer.decode(argument.getArgumentExpression()!!.text)
        val newValue = getIntColorElement(index, argument.getArgumentName()?.asName?.toString(), color)
        if (oldValue != newValue) {
          argument.getArgumentExpression()!!.replace(psiFactory.createExpression(newValue.toString()))
        }
      }
    }
    else {
      for ((index, argument) in valueArguments.withIndex()) {
        val oldValue = Float.parseFloat(argument.getArgumentExpression()!!.text)
        val newValue = getFloatColorElement(index, argument.getArgumentName()?.asName?.toString(), color)
        if (oldValue != newValue) {
          argument.getArgumentExpression()!!.replace(psiFactory.createExpression(newValue.toString() + "f"))
        }
      }
    }
  }

  private fun getIntColorElement(index: Int, name: String?, color: Color): Int {
    if (name == null) {
      return when (index) {
        0 -> color.red
        1 -> color.green
        2 -> color.blue
        3 -> color.alpha

        else -> throw UnsupportedOperationException("$index")
      }
    }
    return when (name) {
      "red" -> color.red
      "green" -> color.green
      "blue" -> color.blue
      "alpha" -> color.alpha

      else -> throw UnsupportedOperationException(name)
    }
  }

  private fun getFloatColorElement(index: Int, name: String?, color: Color): kotlin.Float {
    val components = color.getColorComponents(null)

    if (name == null) {
      return components[index]
    }
    return when (name) {
      "red" -> components[0]
      "green" -> components[1]
      "blue" -> components[2]
      "alpha" -> components[3]

      else -> throw UnsupportedOperationException(name)
    }
  }
}

private class ColorRenderer(
  val color: Color,
  val element: KtCallExpression,
  val editor: (KtCallExpression, Color) -> Unit,
) : GutterIconRenderer(), DumbAware {

  override fun getIcon() = JBUIScale.scaleIcon(ColorIcon(12, color))

  override fun getTooltipText(): String? {
    if (canChooseColor()) {
      @Suppress("DialogTitleCapitalization")
      return IdeBundle.message("dialog.title.choose.color")
    }
    return null
  }

  override fun getClickAction(): AnAction? {
    if (canChooseColor()) {
      return object : AnAction(), ColorListener {
        override fun actionPerformed(e: AnActionEvent) {
          ColorChooserService.instance.showPopup(element.project, color, this)
        }

        override fun colorChanged(newColor: Color, source: Any?) {
          if (color == newColor) {
            return
          }

          val runnable = object : ThrowableRunnable<Exception> {
            override fun run() {
              editor.invoke(element, newColor)
            }
          }

          WriteCommandAction.writeCommandAction(element.project, element.containingFile).run(runnable)
        }
      }
    }
    return null
  }

  override fun isNavigateAction() = canChooseColor()

  private fun canChooseColor() = element.isWritable

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o !is ColorRenderer) return false
    return color == o.color && element == o.element
  }

  override fun hashCode() = Objects.hash(color, element)
}