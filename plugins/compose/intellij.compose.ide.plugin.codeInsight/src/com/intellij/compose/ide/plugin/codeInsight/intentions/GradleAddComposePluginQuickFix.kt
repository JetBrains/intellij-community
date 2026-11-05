// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.codeInsight.intentions

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.compose.ide.plugin.codeInsight.buildScriptKtFile
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer

/**
 * Adds the Compose compiler Gradle plugin to the `plugins` block of the module build script.
 *
 * @param dependencyManager the build system manager that syncs the project after the change.
 * @param version the plugin version to declare, or `null` to declare the plugin without a version.
 */
internal class GradleAddComposePluginQuickFix(
  private val dependencyManager: KotlinBuildSystemDependencyManager,
  private val version: String?,
) : LocalQuickFix {

  override fun getFamilyName(): String = ComposeIdeBundle.message("compose.inspection.missing.plugin.fix.family.name")

  override fun getName(): String = ComposeIdeBundle.message("compose.inspection.missing.plugin.fix.name")

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
    IntentionPreviewInfo.Html(ComposeIdeBundle.message("compose.inspection.missing.plugin.fix.description"))

  override fun startInWriteAction(): Boolean = false

  override fun availableInBatchMode(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val buildScriptFile = ModuleUtilCore.findModuleForPsiElement(descriptor.psiElement)?.buildScriptKtFile ?: return

    val added = WriteCommandAction.writeCommandAction(project, buildScriptFile).compute<Boolean, RuntimeException> {
      buildScriptFile.addComposeCompilerPlugin(version)
    }
    if (added) dependencyManager.startProjectSync()
  }
}

internal fun KtFile.addComposeCompilerPlugin(version: String? = null): Boolean {
  val pluginsBlock = findPluginsBlock()
  val factory = KtPsiFactory.contextual(this)
  val pluginExpression = if (version != null) "$COMPOSE_PLUGIN_EXPRESSION version \"$version\"" else COMPOSE_PLUGIN_EXPRESSION

  return if (pluginsBlock != null) factory.addPluginToExistingBlock(pluginsBlock, pluginExpression)
  else factory.addNewPluginsBlock(this, pluginExpression)
}

private fun KtFile.findPluginsBlock(): KtBlockExpression? {
  val declarations = script?.blockExpression?.statements ?: return null
  val pluginsCall = declarations.asSequence()
                      .filterIsInstance<KtScriptInitializer>()
                      .mapNotNull { it.body as? KtCallExpression }
                      .find { it.calleeExpression?.text == "plugins" } ?: return null

  return pluginsCall.lambdaArguments.singleOrNull()?.getLambdaExpression()?.bodyExpression
         ?: (pluginsCall.valueArguments.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression)?.bodyExpression
}

private fun KtPsiFactory.addPluginToExistingBlock(pluginsBlock: KtBlockExpression, pluginExpression: String): Boolean {
  val pluginStatement = createExpression(pluginExpression)
  pluginsBlock.addBefore(createNewLine(), pluginsBlock.rBrace)
  val added = pluginsBlock.addBefore(pluginStatement, pluginsBlock.rBrace)
  CodeStyleManager.getInstance(pluginsBlock.project).reformat(added)
  return true
}

private fun KtPsiFactory.addNewPluginsBlock(file: KtFile, pluginExpression: String): Boolean {
  val script = file.childrenOfType<KtScript>().firstOrNull()
  val block = script?.blockExpression ?: return false
  val newPluginsBlock = createExpression("plugins {\n$pluginExpression\n}")
  val anchor = block.firstChild
  val added = if (anchor != null) {
    val result = block.addBefore(newPluginsBlock, anchor)
    block.addBefore(createNewLine(2), anchor)
    result
  }
  else {
    block.add(newPluginsBlock)
  }
  CodeStyleManager.getInstance(file.project).reformat(added)
  return true
}

private const val COMPOSE_PLUGIN_EXPRESSION = """id("org.jetbrains.kotlin.plugin.compose")"""