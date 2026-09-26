// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KaptGradleDependenciesManipulator
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KaptProcessorDependency
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtScriptInitializer

internal class KotlinKaptGradleDependenciesManipulator(
    private val scriptFile: KtFile,
) : KaptGradleDependenciesManipulator {

    companion object {
        fun createIfApplicable(scriptFile: KtFile): KotlinKaptGradleDependenciesManipulator? =
            KotlinKaptGradleDependenciesManipulator(scriptFile).takeIf { it.dependenciesBlock != null }
    }

    private val dependenciesBlock: KtBlockExpression?
        get() = scriptFile.findTopLevelBlock("dependencies")

    override fun addDependencies(
        dependencies: List<KaptProcessorDependency>,
    ) {
        val block = dependenciesBlock ?: return
        val sourceDependencyTexts = dependencies.map { it.match.value.trim() }
        val lastSourceDependency = block.statements.lastOrNull { statement ->
            sourceDependencyTexts.any { StringUtil.equalsIgnoreWhitespaces(statement.text, it) }
        } ?: return

        val psiFactory = KtPsiFactory(scriptFile.project)
        var anchor: PsiElement = lastSourceDependency
        val existingDependencyTexts = block.statements.map { it.text }

        for (dependency in dependencies) {
            val dependencyText = dependency.kaptDependencyText
            if (existingDependencyTexts.any { StringUtil.equalsIgnoreWhitespaces(it, dependencyText) }) continue

            anchor = block.addAfter(psiFactory.createExpression(dependencyText), anchor)
                .apply { addNewLinesIfNeeded() }
        }
    }

    override fun removeDependencies(
        dependencies: List<KaptProcessorDependency>,
    ) {
        val block = dependenciesBlock ?: return
        val existingDependencies = block.statements.associateBy { it.text }
        for (dependency in dependencies) {
            existingDependencies[dependency.originalDependencyText]?.delete()
        }
    }

    override fun reformat() {
        val block = dependenciesBlock ?: return
        CodeStyleManager.getInstance(scriptFile.project).reformat(block, true)
    }
}

private fun KtFile.findTopLevelBlock(name: String): KtBlockExpression? =
    PsiTreeUtil.findChildrenOfType(this, KtScriptInitializer::class.java)
        .find { it.text.startsWith(name) }
        ?.getBlock()

private fun KtScriptInitializer.getBlock(): KtBlockExpression? =
    PsiTreeUtil.findChildOfType(this, KtCallExpression::class.java)?.getBlock()

private fun KtCallExpression.getBlock(): KtBlockExpression? =
    (valueArguments.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression)?.bodyExpression
        ?: lambdaArguments.lastOrNull()?.getLambdaExpression()?.bodyExpression

private fun PsiElement.addNewLinesIfNeeded() {
    if (prevSibling != null && prevSibling !is PsiWhiteSpace) {
        parent.addBefore(KtPsiFactory(project).createNewLine(), this)
    }

    if (nextSibling != null && nextSibling !is PsiWhiteSpace) {
        parent.addAfter(KtPsiFactory(project).createNewLine(), this)
    }
}

private val KaptProcessorDependency.kaptDependencyText: String
    get() = dependencyNotation(kaptConfiguration)

private val KaptProcessorDependency.originalDependencyText: String
    get() = dependencyNotation(dependencyConfiguration)

private fun KaptProcessorDependency.dependencyNotation(configuration: String): String =
    "$configuration(\"$notation\")"
