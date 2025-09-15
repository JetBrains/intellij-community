// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.descendantsOfType
import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle

class KotlinAvoidApplyPluginNotationInspectionVisitor(val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        analyze(expression) {
            val symbol = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return
            if (symbol.callableId?.callableName?.asString() != "apply") return
            if (symbol.callableId?.packageName != FqName("org.gradle.kotlin.dsl")) return
        }
        val canBeFixed = expression.valueArguments.singleOrNull()?.getArgumentName()?.asName?.identifier == "plugin"
        val pluginText = expression.valueArguments.singleOrNull()?.getArgumentExpression()
            .asSafely<KtStringTemplateExpression>()?.text

        if (canBeFixed && pluginText != null) {
            holder.registerProblem(
                expression,
                GradleInspectionBundle.message("inspection.message.avoid.apply.plugin.notation.descriptor"),
                GradleMoveApplyPluginToPluginsBlockFix(pluginText)
            )
        } else {
            holder.registerProblem(
                expression,
                GradleInspectionBundle.message("inspection.message.avoid.apply.plugin.notation.descriptor")
            )
        }
    }
}

private class GradleMoveApplyPluginToPluginsBlockFix(
    val pluginText: String
) : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getName(): @IntentionName String {
        return GradleInspectionBundle.message("intention.name.move.apply.to.plugins.block")
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return name
    }

    override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(project, true)
        val file = element.containingKtFile
        val pluginsBlock = file.script?.descendantsOfType<KtScriptInitializer>()?.mapNotNull {
            it.childrenOfType<KtCallExpression>().singleOrNull()
        }?.filter {
            it.calleeExpression?.textMatches("plugins") ?: false
        }?.singleOrNull()?.findDescendantOfType<KtBlockExpression>()
        if (pluginsBlock != null) {
            pluginsBlock.add(psiFactory.createNewLine(1))
            pluginsBlock.add(psiFactory.createExpression("id($pluginText)"))
        } else {
            file.script?.blockExpression?.let {
                it.addBefore(psiFactory.createNewLine(2), it.firstChild)
                it.addBefore(psiFactory.createExpression("plugins {\nid($pluginText)\n}"), it.firstChild)
            }
        }

        element.delete()
    }
}