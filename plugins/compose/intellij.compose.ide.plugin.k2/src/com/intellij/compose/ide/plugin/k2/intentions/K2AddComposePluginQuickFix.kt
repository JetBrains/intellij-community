// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.intentions

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.compose.ide.plugin.k2.buildScriptKtFile
import com.intellij.compose.ide.plugin.k2.refreshGradleProject
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer

internal class K2AddComposePluginQuickFix : LocalQuickFix {

  override fun getFamilyName(): String = ComposeIdeBundle.message("compose.inspection.missing.plugin.fix.family.name")

  override fun getName(): String = ComposeIdeBundle.message("compose.inspection.missing.plugin.fix.name")

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
    IntentionPreviewInfo.Html(ComposeIdeBundle.message("compose.inspection.missing.plugin.fix.description"))

  @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val module = ModuleUtilCore.findModuleForPsiElement(descriptor.psiElement) ?: return

    ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(getName(), project, null) {
      val buildScriptFile = module.buildScriptKtFile ?: return@runWriteActionWithCancellableProgressInDispatchThread
      if (buildScriptFile.addComposeCompilerPlugin()) {
        refreshGradleProject(module)
      }
    }
  }
}

internal fun KtFile.addComposeCompilerPlugin(): Boolean {
  val pluginsBlock = findPluginsBlock()
  val factory = KtPsiFactory.contextual(this)

  return if (pluginsBlock != null) addPluginToExistingBlock(pluginsBlock, factory) else addNewPluginsBlock(factory)
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

private fun addPluginToExistingBlock(pluginsBlock: KtBlockExpression, factory: KtPsiFactory): Boolean {
  val pluginStatement = factory.createExpression(COMPOSE_PLUGIN_EXPRESSION)
  pluginsBlock.addBefore(factory.createNewLine(), pluginsBlock.rBrace)
  val added = pluginsBlock.addBefore(pluginStatement, pluginsBlock.rBrace)
  CodeStyleManager.getInstance(pluginsBlock.project).reformat(added)
  return true
}

private fun KtFile.addNewPluginsBlock(factory: KtPsiFactory): Boolean {
  val script = childrenOfType<KtScript>().firstOrNull()
  val block = script?.blockExpression ?: return false
  val newPluginsBlock = factory.createExpression("plugins {\n$COMPOSE_PLUGIN_EXPRESSION\n}")
  val anchor = block.firstChild
  val added = if (anchor != null) {
    val result = block.addBefore(newPluginsBlock, anchor)
    block.addBefore(factory.createNewLine(2), anchor)
    result
  }
  else {
    block.add(newPluginsBlock)
  }
  CodeStyleManager.getInstance(project).reformat(added)
  return true
}

private const val COMPOSE_PLUGIN_EXPRESSION = """id("org.jetbrains.kotlin.plugin.compose")"""