package org.jetbrains.idea.devkit.fleet.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentsOfType
import org.jetbrains.idea.devkit.fleet.DevKitFleetBundle
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.plainContent
import org.yaml.snakeyaml.Yaml

private val SETTING_DECLARATION_FQ_NAME = FqName("fleet.common.settings.SettingsKey")
private val SETTING_REGISTRATION_FQ_NAME = FqName("fleet.frontend.settings.SettingsRegistrar.setting")
private val RUN_TASK_REGISTRATION_FQ_NAME = FqName("fleet.frontend.run.api.RunTaskRegistrar.task")

internal class DocumentationNotFoundInspection : AbstractKotlinInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    object : KtVisitorVoid() {
      override fun visitCallExpression(expression: KtCallExpression) {
        analyzeCallExpression(expression)?.let {
          if (it.message != null) {
            val anchor = expression.getCallNameExpression() ?: expression
            if(it.fix != null) {
              holder.registerProblem(anchor, it.message, it.fix)
            } else {
              holder.registerProblem(anchor, it.message)
            }
          }
        }
      }
    }
}

internal fun analyzeCallExpression(element: KtCallExpression): DocumentationSearchResult? {
  analyze(element) {
    val call = element.resolveToCall()?.successfulFunctionCallOrNull()
    call?.let {
      if (it.isSettingRegistrationCall()) {
        return visitSettingRegistrationCall(it, element)
      }
      if (it.isRunTaskRegistrationCall()) {
        return visitRunTaskRegistrationCall(it, element)
      }
    }
    val fqName = call?.symbol?.callableId?.asSingleFqName()
                 ?: element.resolveToCall()?.successfulConstructorCallOrNull()?.symbol?.containingClassId?.asSingleFqName()
                 ?: return null
    if (fqName != SETTING_DECLARATION_FQ_NAME) return null
    val property = element.parent as? KtProperty ?: return null
    val module = property.module ?: return null
    val search = ReferencesSearch.search(property, GlobalSearchScope.moduleWithDependentsScope(module))
    return search.asSequence().mapNotNull {
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
  return symbol.callableId?.asSingleFqName() == SETTING_REGISTRATION_FQ_NAME
}

private fun KaFunctionCall<*>.isRunTaskRegistrationCall(): Boolean {
  return symbol.callableId?.asSingleFqName() == RUN_TASK_REGISTRATION_FQ_NAME
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun visitSettingRegistrationCall(call: KaFunctionCall<*>, element: KtCallExpression): DocumentationSearchResult? {
  val argument = call.getParameterValueOrNull("setting") ?: return null
  val variable = argument.resolveToCall()?.successfulVariableAccessCall()?.symbol ?: return null
  val settingsKeyCall = (variable as? KaPropertySymbol)?.initializer?.initializerPsi?.resolveToCall()?.successfulFunctionCallOrNull()
                        ?: return null
  val keyArgument = settingsKeyCall.getParameterValueOrNull("key") ?: return null
  val key = keyArgument.getStringValue() ?: return null
  val documentationStatus = getDocumentation(element)
  val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
  return if (documentationStatus is DocumentationStatus.Exists) {
    if (key !in documentationStatus.documentedSettings) {
      DocumentationSearchResult(
        DevKitFleetBundle.message("setting.isnt.documented", key),
        NavigateToDocumentationFix(module),
        documentationStatus.file
      )
    }
    else DocumentationSearchResult(file = documentationStatus.file)
  }
  else {
    DocumentationSearchResult(
      DevKitFleetBundle.message("documentation.yaml.not.found"),
      CreateDocumentationFileFix(module)
    )
  }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun visitRunTaskRegistrationCall(call: KaFunctionCall<*>, element: KtCallExpression): DocumentationSearchResult? {
  val argument = call.getParameterValueOrNull("taskType") ?: return null
  val variable = argument.resolveToCall()?.successfulVariableAccessCall()?.symbol ?: return null
  val taskTypeCall = (variable as? KaPropertySymbol)?.initializer?.initializerPsi?.resolveToCall()?.successfulFunctionCallOrNull()
                     ?: return null
  val idArgument = taskTypeCall.getParameterValueOrNull("id") ?: return null
  val id = idArgument.getStringValue() ?: return null
  val documentationStatus = getDocumentation(element)
  val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
  return if (documentationStatus is DocumentationStatus.Exists) {
    if (id !in documentationStatus.documentedRunConfigs) {
      DocumentationSearchResult(
        DevKitFleetBundle.message("run.configuration.isnt.documented", id),
        NavigateToDocumentationFix(module),
        documentationStatus.file
      )
    }
    else DocumentationSearchResult(file = documentationStatus.file)
  }
  else {
    DocumentationSearchResult(
      DevKitFleetBundle.message("documentation.yaml.not.found"),
      CreateDocumentationFileFix(module)
    )
  }
}

private fun KaFunctionCall<*>.getParameterValueOrNull(name: String): KtExpression? =
  argumentMapping.firstNotNullOfOrNull { (expr, par) ->
    if (par.name.toString() == name) expr
    else null
  }

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun KtExpression.getStringValue(): String? {
  (this as? KtStringTemplateExpression)?.plainContent?.let { return it }
  val variable = resolveToCall()?.successfulVariableAccessCall()?.symbol ?: return null
  return (variable as? KaPropertySymbol)?.initializer?.initializerPsi?.getStringValue()
}

sealed interface DocumentationStatus {
  data class Exists(val file: VirtualFile, val documentedSettings: Set<String>, val documentedRunConfigs: Set<String>) : DocumentationStatus
  data object Missing : DocumentationStatus
}

private fun getDocumentation(element: PsiElement): DocumentationStatus? {
  val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
  val moduleRootManager = ModuleRootManager.getInstance(module)
  val resources = moduleRootManager.getSourceRoots(JavaModuleSourceRootTypes.RESOURCES)
  val file = resources.firstOrNull { it.name == "resources" }?.children?.firstOrNull { it.name == "documentation.yaml" }
             ?: return DocumentationStatus.Missing
  val documentation = CachedValuesManager.getManager(module.project).getCachedValue(module) {
    val res = file.let {
      val configText = it.readText()
      val config = Yaml().load<Any>(configText)
      config?.let {
        val settings = (config as? Map<*, *>)?.get("settings")?.let {
          (it as? Map<String, *>)?.keys
        } ?: emptySet()
        val runConfigs = (config as? Map<*, *>)?.get("runConfigurations")?.let {
          (it as? Map<String, *>)?.keys
        } ?: emptySet()
        DocumentationStatus.Exists(file, settings, runConfigs)
      }
    } ?: DocumentationStatus.Missing
    CachedValueProvider.Result.create(res, file)
  }
  return documentation
}

internal data class DocumentationSearchResult(val message: String? = null, val fix: LocalQuickFix? = null, val file: VirtualFile? = null)

private class CreateDocumentationFileFix(val module: Module) : LocalQuickFix {
  override fun getName() = DevKitFleetBundle.message("intention.name.create.documentation.yaml.file")
  override fun getFamilyName() = DevKitFleetBundle.message("intention.family.name.create.documentation")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val resourcesRoot =
      moduleRootManager.getSourceRoots(JavaModuleSourceRootTypes.RESOURCES).firstOrNull()
      ?: let {
        val dir = moduleRootManager.contentRoots.firstOrNull()?.findOrCreateDirectory("resources") ?: return
        MarkRootsManager.modifyRoots(module, arrayOf(dir)) { file: VirtualFile, entry: ContentEntry ->
          entry.addSourceFolder(file, JavaResourceRootType.RESOURCE)
        }
        dir
      }
    val virtualFile = resourcesRoot.createChildData(this, "documentation.yaml")
    virtualFile.setBinaryContent("""
      settings:
        # Add your settings here
      runConfigurations:
        # Add your run configurations here
    """.trimIndent().toByteArray())

    val fileEditorManager = FileEditorManager.getInstance(project)
    fileEditorManager.openFile(virtualFile, true)
  }
}

private class NavigateToDocumentationFix(val module: Module) : LocalQuickFix {
  override fun getName() = DevKitFleetBundle.message("intention.name.navigate.to.documentation.yaml")
  override fun getFamilyName() = DevKitFleetBundle.message("intention.family.name.navigate.to.documentation")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val virtualFile = moduleRootManager.getSourceRoots(JavaModuleSourceRootTypes.RESOURCES)
                        .flatMap { it.children.toList() }
                        .firstOrNull { it.name == "documentation.yaml" }
                      ?: return

    val fileEditorManager = FileEditorManager.getInstance(project)
    fileEditorManager.openFile(virtualFile, true)
  }
}
