// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.devkit.projectRoots.IntelliJPlatformProduct
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.plugins.gradle.util.GradleConstants
import javax.swing.Icon

private const val DEPENDENCIES_BLOCK = "dependencies"
private const val INTELLIJ_PLATFORM_BLOCK = "intellijPlatform"
private const val CREATE_HELPER = "create"
private const val TYPE_PARAMETER = "type"
private const val VERSION_PARAMETER = "version"
private const val ID_PARAMETER = "id"
private const val IDS_PARAMETER = "ids"
private const val COMPLETION_PRIORITY = 100.0

private val BUNDLED_PLUGIN_HELPERS = setOf("bundledPlugin", "bundledPlugins", "testBundledPlugin", "testBundledPlugins")
private val BUNDLED_MODULE_HELPERS = setOf("bundledModule", "bundledModules", "testBundledModule", "testBundledModules")

/** Completes IntelliJ Platform dependency versions, bundled plugins, and bundled modules from metadata imported during Gradle sync. */
internal class IntelliJPlatformVersionsCompletionContributor : CompletionContributor() {

  init {
    val place = PlatformPatterns.psiElement()
      .withParent(KtLiteralStringTemplateEntry::class.java)
      .with(object : PatternCondition<PsiElement>("intellijPlatformLiteral") {
        override fun accepts(element: PsiElement, context: ProcessingContext?): Boolean {
          if (!element.containingFile.name.endsWith(GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)) return false

          val callExpression = element.getParentOfType<KtCallExpression>(true) ?: return false

          val intellijPlatformClosure =
            callExpression.getParentOfType<KtCallExpression>(true)?.takeIf { it.calleeExpression?.text == INTELLIJ_PLATFORM_BLOCK }
            ?: return false

          intellijPlatformClosure.getParentOfType<KtCallExpression>(true)?.takeIf { it.calleeExpression?.text == DEPENDENCIES_BLOCK }
          ?: return false

          val calleeName = callExpression.calleeExpression?.text
          if (calleeName in BUNDLED_PLUGIN_HELPERS || calleeName in BUNDLED_MODULE_HELPERS) {
            return callExpression.isBundledArgument(element)
          }

          return callExpression.isVersionArgument(element) || (callExpression.containsInLambda(element) && element.isVersionAssignmentValue())
        }
      })

    extend(CompletionType.BASIC, place, IntelliJPlatformVersionsCompletionProvider())
  }

  private fun KtCallExpression.isBundledArgument(element: PsiElement): Boolean {
    val argument = valueArguments.firstOrNull { PsiTreeUtil.isAncestor(it, element, false) } ?: return false
    val argumentName = argument.getArgumentName()?.asName?.asString()
    if (argumentName != null) return argumentName == ID_PARAMETER || argumentName == IDS_PARAMETER
    return true
  }

  private fun KtCallExpression.isVersionArgument(element: PsiElement): Boolean {
    val argument = valueArguments.firstOrNull { PsiTreeUtil.isAncestor(it, element, false) } ?: return false
    val argumentName = argument.getArgumentName()?.asName?.asString()
    if (argumentName != null) return argumentName == VERSION_PARAMETER

    val versionArgumentIndex = if (calleeExpression?.text == CREATE_HELPER) 1 else 0
    return valueArguments.indexOf(argument) == versionArgumentIndex
  }

  private fun KtCallExpression.containsInLambda(element: PsiElement): Boolean {
    return lambdaArguments.any { PsiTreeUtil.isAncestor(it, element, false) }
  }

  private fun PsiElement.isVersionAssignmentValue(): Boolean {
    val assignment = getParentOfType<KtBinaryExpression>(strict = false) ?: return false
    val property = assignment.left as? KtNameReferenceExpression ?: return false
    val value = assignment.right ?: return false

    return assignment.operationToken == KtTokens.EQ &&
           property.getReferencedName() == VERSION_PARAMETER &&
           PsiTreeUtil.isAncestor(value, this, false)
  }

  private class IntelliJPlatformVersionsCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val callExpression = parameters.position.getParentOfType<KtCallExpression>(true) ?: return
      val dependencyHelper = callExpression.calleeExpression?.text ?: return
      val model = IntelliJPlatformGradleModelProvider.getInstance(parameters.position.project).getModel(parameters.originalFile) ?: return
      val resultSet = result.caseInsensitive()

      when (dependencyHelper) {
        in BUNDLED_PLUGIN_HELPERS -> resultSet.addBundledPluginCompletions(model)
        in BUNDLED_MODULE_HELPERS -> resultSet.addBundledModuleCompletions(model)
        CREATE_HELPER -> resultSet.addCreateHelperCompletions(callExpression, model)
        in model.dependencyHelperProductCodes -> resultSet.addDependencyHelperCompletions(dependencyHelper, model)
      }
    }

    private fun CompletionResultSet.addBundledPluginCompletions(model: IntelliJPlatformGradleData) =
      addBundledArtifacts(model.bundledPlugins, AllIcons.Nodes.Plugin, "Bundled Plugin")

    private fun CompletionResultSet.addBundledModuleCompletions(gradleModel: IntelliJPlatformGradleData) =
      addBundledArtifacts(gradleModel.bundledModules, DevkitCoreIcons.PluginModule, "Bundled Module")

    private fun CompletionResultSet.addCreateHelperCompletions(callExpression: KtCallExpression, model: IntelliJPlatformGradleData) =
      addProductVersionCompletions(callExpression.getCreateProductCode(model), model)

    private fun CompletionResultSet.addDependencyHelperCompletions(dependencyHelper: String, model: IntelliJPlatformGradleData) =
      addProductVersionCompletions(model.dependencyHelperProductCodes[dependencyHelper], model)

    private fun CompletionResultSet.addProductVersionCompletions(productCode: String?, gradleModel: IntelliJPlatformGradleData) {
      productCode ?: return

      val productName = IntelliJPlatformProduct.fromProductCode(productCode)?.getName() ?: "IntelliJ Platform"
      val releases = gradleModel.productReleases[productCode].orEmpty().asSequence().distinct().sortedWith { first, second ->
        StringUtil.compareVersionNumbers(second.version, first.version).takeIf { it != 0 } ?: compareValues(first.channel, second.channel)
      }.toList()

      releases.forEachIndexed { index, release ->
        val channel = release.channel.toPresentableChannel()
        val lookupElement =
          LookupElementBuilder.create(release.version)
            .withIcon(DevkitCoreIcons.Sdk_closed)
            .withTailText(" $channel", true)
            .withTypeText("$productName ($productCode)", true)
        addElement(PrioritizedLookupElement.withPriority(lookupElement, COMPLETION_PRIORITY + releases.size - index))
      }
    }

    private fun CompletionResultSet.addBundledArtifacts(artifacts: List<IntelliJPlatformBundledArtifact>, icon: Icon, typeText: String) {
      val distinctArtifacts = artifacts.asSequence().distinctBy { it.id }.sortedBy { it.id }.toList()

      distinctArtifacts.forEachIndexed { index, artifact ->
        val lookupElement = LookupElementBuilder.create(artifact.id).withIcon(icon)
          .withTailText(if (artifact.name.isNotEmpty()) " (${artifact.name})" else "", true).withTypeText(typeText, true)
        addElement(PrioritizedLookupElement.withPriority(lookupElement, COMPLETION_PRIORITY + distinctArtifacts.size - index))
      }
    }

    private fun KtCallExpression.getCreateProductCode(gradleModel: IntelliJPlatformGradleData): String? {
      val typeArgument = valueArguments.firstOrNull {
        it.getArgumentName()?.asName?.asString() == TYPE_PARAMETER
      } ?: valueArguments.firstOrNull { it.getArgumentName() == null }
      val typeExpression = typeArgument?.getArgumentExpression() as? KtStringTemplateExpression ?: return null

      val rawProductCode = (typeExpression.entries.singleOrNull() as? KtLiteralStringTemplateEntry)?.text ?: return null
      return gradleModel.productReleases.keys.firstOrNull { it.equals(rawProductCode, ignoreCase = true) } ?: rawProductCode
    }

    private fun String.toPresentableChannel(): String {
      return when (this) {
        "EAP", "RC" -> this
        else -> lowercase().replaceFirstChar(Char::titlecase)
      }
    }
  }
}
