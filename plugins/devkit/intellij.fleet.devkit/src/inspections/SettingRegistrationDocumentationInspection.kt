package org.jetbrains.idea.devkit.fleet.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentsOfType
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.plainContent
import org.yaml.snakeyaml.Yaml

internal class SettingRegistrationDocumentationInspection : AbstractKotlinInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    object : KtVisitorVoid() {
      override fun visitCallExpression(expression: KtCallExpression) {
        val error = analyzeCallExpression(expression)
        if (error != null) {
          holder.registerProblem(expression.getCallNameExpression(), error)
        }
      }
    }

  private fun analyzeCallExpression(element: KtCallExpression): String? {
    analyze(element) {
      val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
      if (call.isSettingRegistrationCall()) {
        return visitSettingRegistrationCall(call, element)
      }
      if (call.isRunTaskRegistrationCall()) {
        return visitRunTaskRegistrationCall(call, element)
      }
      if (call.symbol.callableId?.asSingleFqName() != FqName("fleet.common.settings.SettingsKey")) return null
      val property = element.parent as? KtProperty ?: return null
      val search = ReferencesSearch.search(property, GlobalSearchScope.projectScope(property.project))
      return search.mapNotNull {
        val callExpr = it.element.parentsOfType<KtCallExpression>().firstOrNull() ?: return@mapNotNull null
        val kaCall = callExpr.resolveToCall()?.successfulFunctionCallOrNull() ?: return@mapNotNull null
        if (kaCall.isSettingRegistrationCall()) {
          visitSettingRegistrationCall(kaCall, callExpr)
        }
        else null
      }.firstOrNull()
    }
  }

  private fun KaFunctionCall<*>.isSettingRegistrationCall(): Boolean {
    return symbol.callableId?.asSingleFqName() == FqName("fleet.frontend.settings.SettingsRegistrar.setting")
  }

  private fun KaFunctionCall<*>.isRunTaskRegistrationCall(): Boolean {
    return symbol.callableId?.asSingleFqName() == FqName("fleet.frontend.run.api.RunTaskRegistrar.task")
  }

  context(KaSession)
  @OptIn(KaExperimentalApi::class)
  private fun visitSettingRegistrationCall(call: KaFunctionCall<*>, element: KtCallExpression): String? {
    val argument = call.argumentMapping.firstNotNullOfOrNull { (expr, par) ->
      if (par.name.toString() == "setting") expr
      else null
    } ?: return null
    val variable = argument.resolveToCall()?.successfulVariableAccessCall()?.symbol ?: return null
    val settingsKeyCall = (variable as? KaPropertySymbol)?.initializer?.initializerPsi?.resolveToCall()?.successfulFunctionCallOrNull()
                          ?: return null
    val keyArgument = settingsKeyCall.argumentMapping.firstNotNullOfOrNull { (expr, par) ->
      if (par.name.toString() == "key") expr
      else null
    } ?: return null
    val key = keyArgument.getStringValue() ?: return null
    val documentationStatus = getDocumentation(element)
    return if (documentationStatus is DocumentationStatus.Exists) {
      if (key !in documentationStatus.documentedSettings) {
        "Setting isn't documented"
      }
      else null
    }
    else {
      "no documentation found"
    }
  }

  context(KaSession)
  @OptIn(KaExperimentalApi::class)
  private fun visitRunTaskRegistrationCall(call: KaFunctionCall<*>, element: KtCallExpression): String? {
    val argument = call.argumentMapping.firstNotNullOfOrNull { (expr, par) ->
      if (par.name.toString() == "taskType") expr
      else null
    } ?: return null
    val variable = argument.resolveToCall()?.successfulVariableAccessCall()?.symbol ?: return null
    val taskTypeCall = (variable as? KaPropertySymbol)?.initializer?.initializerPsi?.resolveToCall()?.successfulFunctionCallOrNull()
                          ?: return null
    val idArgument = taskTypeCall.argumentMapping.firstNotNullOfOrNull { (expr, par) ->
      if (par.name.toString() == "id") expr
      else null
    } ?: return null
    val id = idArgument.getStringValue() ?: return null
    val documentationStatus = getDocumentation(element)
    return if (documentationStatus is DocumentationStatus.Exists) {
      if (id !in documentationStatus.documentedRunConfigs) {
        "Run Task isn't documented"
      }
      else null
    }
    else {
      "no documentation found"
    }
  }

  context(KaSession)
  @OptIn(KaExperimentalApi::class)
  private fun KtExpression.getStringValue(): String? {
    (this as? KtStringTemplateExpression)?.plainContent?.let { return it }
    val variable = resolveToCall()?.successfulVariableAccessCall()?.symbol ?: return null
    return (variable as? KaPropertySymbol)?.initializer?.initializerPsi?.getStringValue()
  }

  sealed interface DocumentationStatus {
    data class Exists(val documentedSettings: Set<String>, val documentedRunConfigs: Set<String>) : DocumentationStatus
    data object Missing : DocumentationStatus
  }

  private fun getDocumentation(element: PsiElement): DocumentationStatus? {
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val resources = moduleRootManager.getSourceRoots(JavaModuleSourceRootTypes.RESOURCES)
    val file = resources.firstOrNull { it.name == "resources" }?.children?.firstOrNull { it.name == "documentation.yaml" } ?: return DocumentationStatus.Missing
    val documentation = CachedValuesManager.getManager(module.project).getCachedValue(module) {
      val res = file?.let {
        val configText = it.readText()
        val config = Yaml().load<Any>(configText)
        config?.let {
          val settings = (config as? Map<*, *>)?.get("settings")?.let {
            (it as? Map<String, *>)?.keys
          } ?: emptySet()
          val runConfigs = (config as? Map<*, *>)?.get("runConfigurations")?.let {
            (it as? Map<String, *>)?.keys
          } ?: emptySet()
          DocumentationStatus.Exists(settings, runConfigs)
        }
      } ?: DocumentationStatus.Missing
      CachedValueProvider.Result.create(res, file)
    }
    return documentation
  }

  internal class TodoFix() : KotlinModCommandQuickFix<KtCallExpression>() {
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
}