// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.gradle.scripting.shared.isGradleKotlinScript
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames

internal class KotlinGradlePropertyMethodCallQuickFixRegistrar : KotlinQuickFixRegistrar() {

  private val fixes = KtQuickFixesListBuilder.registerPsiQuickFix {
    registerFactory(UNRESOLVED_REFERENCE_FACTORY)
    registerFactory(UNRESOLVED_REFERENCE_WRONG_RECEIVER_FACTORY)
  }

  override val list: KotlinQuickFixesList = KotlinQuickFixesList.createCombined(fixes)
}

private val UNRESOLVED_REFERENCE_FACTORY = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
  createGradlePropertyMethodCallQuickFixes(diagnostic.psi)
}

private val UNRESOLVED_REFERENCE_WRONG_RECEIVER_FACTORY =
  KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReferenceWrongReceiver ->
    createGradlePropertyMethodCallQuickFixes(diagnostic.psi)
  }

context(_: KaSession)
private fun createGradlePropertyMethodCallQuickFixes(psi: PsiElement): List<ModCommandAction> {
  val qualifiedExpression = psi.gradlePropertyMethodCall() ?: return emptyList()
  if (!qualifiedExpression.isInGradleKotlinDslContext()) return emptyList()
  if (!qualifiedExpression.hasGradlePropertyWrapperReceiver()) return emptyList()
  return listOf(UnwrapGradlePropertyMethodCallQuickFix(qualifiedExpression))
}

private class UnwrapGradlePropertyMethodCallQuickFix(
  element: KtDotQualifiedExpression,
) : PsiUpdateModCommandAction<KtDotQualifiedExpression>(element), DumbAware {

  override fun getFamilyName(): @IntentionFamilyName String =
    GradleInspectionBundle.message("intention.name.gradle.property.method.call.unwrap")

  override fun getPresentation(
    context: ActionContext,
    element: KtDotQualifiedExpression,
  ): Presentation = Presentation.of(familyName).withPriority(PriorityAction.Priority.HIGH)

  override fun invoke(
    actionContext: ActionContext,
    element: KtDotQualifiedExpression,
    updater: ModPsiUpdater,
  ) {
    val callExpression = element.selectorExpression as? KtCallExpression ?: return
    val replacement = KtPsiFactory(actionContext.project).createExpressionByPattern(
      "$0.get().$1",
      element.receiverExpression,
      callExpression,
    )
    element.replace(replacement)
  }
}

private fun PsiElement.gradlePropertyMethodCall(): KtDotQualifiedExpression? {
  val callExpression = when (this) {
    is KtNameReferenceExpression -> parent as? KtCallExpression
    is KtCallExpression -> this
    is KtDotQualifiedExpression -> selectorExpression as? KtCallExpression
    else -> parentOfType<KtCallExpression>(withSelf = true)
  } ?: return null

  if (callExpression.calleeExpression !is KtNameReferenceExpression) return null
  if (this is KtNameReferenceExpression && callExpression.calleeExpression != this) return null

  val qualifiedExpression = callExpression.parent as? KtDotQualifiedExpression ?: return null
  if (qualifiedExpression.selectorExpression != callExpression) return null

  return qualifiedExpression
}

context(_: KaSession)
private fun KtDotQualifiedExpression.hasGradlePropertyWrapperReceiver(): Boolean {
  val receiverType = receiverExpression.expressionType
    ?: (receiverExpression.mainReference?.resolveToSymbol() as? KaVariableSymbol)?.returnType
    ?: return false
  return receiverType.isGradlePropertyWrapperType()
}

private fun KaType.isGradlePropertyWrapperType(): Boolean {
  val classType = this as? KaClassType
  return classType?.classId in SUPPORTED_GRADLE_PROPERTY_CLASS_IDS
}

private fun KtDotQualifiedExpression.isInGradleKotlinDslContext(): Boolean =
  isGradleKotlinScript(containingKtFile.alwaysVirtualFile)

private val SUPPORTED_GRADLE_PROPERTY_CLASS_IDS = setOf(
  topLevelClassId(GradleCommonClassNames.GRADLE_API_PROVIDER_PROPERTY),
  topLevelClassId(GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER),
  topLevelClassId(GradleCommonClassNames.GRADLE_API_PROVIDER_LIST_PROPERTY),
  topLevelClassId(GradleCommonClassNames.GRADLE_API_PROVIDER_SET_PROPERTY),
  topLevelClassId(GradleCommonClassNames.GRADLE_API_PROVIDER_MAP_PROPERTY),
)

private fun topLevelClassId(fqn: String): ClassId = ClassId.topLevel(FqName(fqn))
