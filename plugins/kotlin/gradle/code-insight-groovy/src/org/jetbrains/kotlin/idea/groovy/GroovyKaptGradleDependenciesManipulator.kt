// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.groovy

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KaptGradleDependenciesManipulator
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KaptProcessorDependency
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

internal class GroovyKaptGradleDependenciesManipulator(
    private val scriptFile: GroovyFile,
) : KaptGradleDependenciesManipulator {

    companion object {
        fun createIfApplicable(scriptFile: GroovyFile): GroovyKaptGradleDependenciesManipulator? =
            GroovyKaptGradleDependenciesManipulator(scriptFile)
                .takeIf { it.dependenciesBlock != null }
    }

    private val dependenciesBlock: GrClosableBlock?
        get() = scriptFile.findTopLevelBlock("dependencies")

    override fun addDependencies(
        dependencies: List<KaptProcessorDependency>,
    ) {
        val block = dependenciesBlock ?: return
        val statements = block.statements

        val sourceDependencyTexts = dependencies.mapTo(mutableSetOf()) { it.originalDependencyText }

        val lastSourceDependency = statements.lastOrNull { statement ->
            sourceDependencyTexts.any(statement.text::equalsIgnoringWhitespaces)
        } ?: return

        val psiFactory = GroovyPsiElementFactory.getInstance(scriptFile.project)
        val existingDependencyTexts = statements.mapTo(mutableListOf(), GrStatement::getText)

        var anchor: PsiElement = lastSourceDependency

        for (dependency in dependencies.distinctBy { it.kaptConfiguration to it.notation }) {
            val dependencyText = dependency.kaptDependencyText

            if (existingDependencyTexts.any(dependencyText::equalsIgnoringWhitespaces)) {
                continue
            }

            val statement = psiFactory.createStatementFromText(dependencyText, block)

            anchor = block.addAfter(statement, anchor).apply {
                addNewLinesIfNeeded()
            }

            existingDependencyTexts += dependencyText
        }
    }

    override fun removeDependencies(
        dependencies: List<KaptProcessorDependency>,
    ) {
        val block = dependenciesBlock ?: return

        for (dependency in dependencies) {
            block.statements
                .firstOrNull { it.text.equalsIgnoringWhitespaces(dependency.originalDependencyText) }
                ?.delete()
        }
    }

    override fun reformat() {
        val block = dependenciesBlock ?: return
        CodeStyleManager.getInstance(scriptFile.project).reformat(block, true)
    }
}

private fun GroovyFile.findTopLevelBlock(name: String): GrClosableBlock? =
    PsiTreeUtil.getChildrenOfTypeAsList(this, GrStatement::class.java)
        .asSequence()
        .filterIsInstance<GrMethodCall>()
        .firstOrNull { it.invokedExpression.text == name }
        ?.closureArguments
        ?.singleOrNull()

private fun PsiElement.addNewLinesIfNeeded() {
    val factory = GroovyPsiElementFactory.getInstance(project)

    if (prevSibling != null && prevSibling !is PsiWhiteSpace) {
        parent.addBefore(factory.createLineTerminator(1), this)
    }

    if (nextSibling != null && nextSibling !is PsiWhiteSpace) {
        parent.addAfter(factory.createLineTerminator(1), this)
    }
}

private fun String.equalsIgnoringWhitespaces(other: String): Boolean =
    StringUtil.equalsIgnoreWhitespaces(this, other)

private val KaptProcessorDependency.originalDependencyText: String
    get() = match.value.trim()

private val KaptProcessorDependency.kaptDependencyText: String
    get() = if (originalDependencyUsesParentheses) {
        "$kaptConfiguration('$notation')"
    } else {
        "$kaptConfiguration '$notation'"
    }

private val KaptProcessorDependency.originalDependencyUsesParentheses: Boolean
    get() = originalDependencyText
        .removePrefix(dependencyConfiguration)
        .trimStart()
        .startsWith("(")
