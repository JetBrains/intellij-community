package org.jetbrains.idea.devkit.fleet.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.debugText.getDebugText

internal class SettingIsDocumentedInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, String>() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
  ) = object : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
      visitTargetElement(expression, holder, isOnTheFly)
    }
  }

  override fun getProblemDescription(
    element: KtCallExpression,
    @InspectionMessage context: String,
  ): @InspectionMessage String {
    return context
  }

  override fun createQuickFix(
    element: KtCallExpression,
    context: String,
  ): KotlinModCommandQuickFix<KtCallExpression> {
    return TodoFix()
  }

  context(KaSession)
  @OptIn(KaExperimentalApi::class)
  override fun prepareContext(element: KtCallExpression): String? {
    val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
    if(call.symbol.callableId?.asSingleFqName() != FqName("fleet.frontend.settings.SettingsRegistrar.setting")) return null
    val argument = call.argumentMapping.firstNotNullOfOrNull { (expr, par) ->
      if (par.name.toString() == "setting") expr
      else null
    } ?: return null
    val variable = argument.resolveToCall()?.successfulVariableAccessCall()?.symbol ?: return null
    val settingsKeyCall = (variable as? KaPropertySymbol)?.initializer?.initializerPsi?.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
    val keyArgument = settingsKeyCall.argumentMapping.firstNotNullOfOrNull { (expr, par) ->
      if (par.name.toString() == "key") expr
      else null
    } ?: return null
    return (keyArgument as? KtStringTemplateExpression)?.getDebugText()
  }
}


private class TodoFix() : KotlinModCommandQuickFix<KtCallExpression>() {
  override fun getName(): String = "TODO"

  override fun getFamilyName(): String = name

  override fun applyFix(
    project: Project,
    element: KtCallExpression,
    updater: ModPsiUpdater,
  ) {
    element.delete()
  }
}