// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.UseHtmlChunkToolTipFixProviders
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Reports usages of [javax.swing.JComponent.setToolTipText] with a [String] argument
 * and suggests using [com.intellij.ide.setToolTipText] to prevent accidental HTML injections.
 *
 * Detects both explicit method calls (`comp.setToolTipText(text)`) and
 * Kotlin property-style setters (`comp.toolTipText = text`).
 */
internal class UseHtmlChunkToolTipInspection : DevKitUastInspectionBase() {
  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return DevKitInspectionUtil.isAllowed(holder.file) &&
           DevKitInspectionUtil.isClassAvailable(holder, HTML_CHUNK_FQN) &&
           DevKitInspectionUtil.isClassAvailable(holder, HELP_TOOLTIP_KT_FQN)
  }

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        if (node.methodName != SET_TIP_TEXT_METHOD_NAME) return super.visitCallExpression(node)
        val method = node.resolve() ?: return super.visitCallExpression(node)
        if (!isSetToolTipTextOnJComponent(method)) return super.visitCallExpression(node)
        if (isNullLiteral(node.valueArguments.firstOrNull())) return super.visitCallExpression(node)

        val psi = node.sourcePsi ?: return super.visitCallExpression(node)
        registerProblem(psi)
        return super.visitCallExpression(node)
      }

      override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
        if (node.operator != UastBinaryOperator.ASSIGN) return super.visitBinaryExpression(node)
        val method = node.resolveOperator() ?: return super.visitBinaryExpression(node)
        if (method.name != SET_TIP_TEXT_METHOD_NAME) return super.visitBinaryExpression(node)
        if (!isSetToolTipTextOnJComponent(method)) return super.visitBinaryExpression(node)
        if (isNullLiteral(node.rightOperand)) return super.visitBinaryExpression(node)

        val psi = node.sourcePsi ?: return super.visitBinaryExpression(node)
        registerProblem(psi)
        return super.visitBinaryExpression(node)
      }

      private fun registerProblem(psi: com.intellij.psi.PsiElement) {
        val fixProvider = UseHtmlChunkToolTipFixProviders.forLanguage(psi.language)
        val fixes = fixProvider?.createFixes(psi) ?: LocalQuickFix.EMPTY_ARRAY
        holder.registerProblem(psi, DevKitBundle.message("inspections.use.html.chunk.tooltip.message"), *fixes)
      }
    }, HINTS)
  }
}

private const val SET_TIP_TEXT_METHOD_NAME = "setToolTipText"
private const val JCOMPONENT_FQN = "javax.swing.JComponent"
private const val HTML_CHUNK_FQN = "com.intellij.openapi.util.text.HtmlChunk"
private const val HELP_TOOLTIP_KT_FQN = "com.intellij.ide.HelpTooltipKt"

private val HINTS: Array<Class<out UElement>> = arrayOf(UCallExpression::class.java, UBinaryExpression::class.java)

private fun isNullLiteral(expr: UExpression?): Boolean {
  return expr is ULiteralExpression && expr.isNull
}

private fun isSetToolTipTextOnJComponent(method: PsiMethod): Boolean {
  val clazz = method.containingClass ?: return false
  if (!InheritanceUtil.isInheritor(clazz, JCOMPONENT_FQN)) return false
  val params = method.parameterList.parameters
  return params.size == 1 && params[0].type.canonicalText == CommonClassNames.JAVA_LANG_STRING
}
