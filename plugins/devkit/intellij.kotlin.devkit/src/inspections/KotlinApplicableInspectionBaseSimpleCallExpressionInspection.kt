// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.getOrCreateClassBody
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal class KotlinApplicableInspectionBaseSimpleCallExpressionInspection : com.intellij.codeInspection.LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!isAllowed(holder)) return PsiElementVisitor.EMPTY_VISITOR

    return object : KtVisitorVoid() {
      override fun visitClass(klass: KtClass) {
        if (klass.nameIdentifier == null) return
        if (klass.hasOwnGetApplicableRangesOverride()) return

        val shouldOverride = analyze(klass) {
          val symbol = klass.symbol as? KaClassSymbol ?: return@analyze false
          symbol.superTypes.any { supertype ->
            supertype.symbol?.classId?.asSingleFqName() == KOTLIN_APPLICABLE_INSPECTION_BASE_SIMPLE_FQN &&
            supertype is KaClassType &&
            supertype.typeArguments.firstOrNull()?.type?.symbol?.classId?.asSingleFqName() == KT_CALL_EXPRESSION_FQN
          }
        }
        if (!shouldOverride) return

        holder.registerProblem(
          klass.nameIdentifier!!,
          DevKitKotlinBundle.message("inspection.kotlin.applicable.inspection.base.simple.call.expression.message"),
          AddGetApplicableRangesOverrideFix(),
        )
      }
    }
  }

  private fun isAllowed(holder: ProblemsHolder): Boolean {
    return DevKitInspectionUtil.isAllowed(holder.file) &&
           DevKitInspectionUtil.isClassAvailable(holder, KOTLIN_APPLICABLE_INSPECTION_BASE_FQN.asString()) &&
           DevKitInspectionUtil.isClassAvailable(holder, KT_CALL_EXPRESSION_FQN.asString())
  }

  private class AddGetApplicableRangesOverrideFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): @IntentionFamilyName String =
      DevKitKotlinBundle.message("inspection.kotlin.applicable.inspection.base.simple.call.expression.fix.family.name")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      val klass = element.getParentOfType<KtClass>(strict = false) ?: return
      val body = updater.getWritable(klass).getOrCreateClassBody()
      val function = KtPsiFactory(project).createFunction("""
        override fun getApplicableRanges(element: org.jetbrains.kotlin.psi.KtCallExpression): List<com.intellij.openapi.util.TextRange> {
          return org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges.calleeExpression(element)
        }
      """.trimIndent())
      val addedFunction = body.addBefore(function, body.rBrace) as KtNamedFunction
      ShortenReferencesFacility.getInstance().shorten(addedFunction as KtElement)
    }
  }
}

private fun KtClass.hasOwnGetApplicableRangesOverride(): Boolean {
  return declarations.filterIsInstance<KtNamedFunction>().any { function ->
    function.name == "getApplicableRanges" && function.hasModifier(KtTokens.OVERRIDE_KEYWORD)
  }
}

private val KOTLIN_APPLICABLE_INSPECTION_BASE_FQN =
  FqName("org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase")
private val KOTLIN_APPLICABLE_INSPECTION_BASE_SIMPLE_FQN =
  FqName("org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase.Simple")
private val KT_CALL_EXPRESSION_FQN = FqName("org.jetbrains.kotlin.psi.KtCallExpression")
