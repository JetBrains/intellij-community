// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner.fullyExpandCall
import org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceWithData
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.replaceUsagesInWholeProject
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*

class DeprecatedSymbolUsageInWholeProjectFix(
    element: KtReferenceExpression,
    replaceWith: ReplaceWithData,
    @Nls private val text: String
) : DeprecatedSymbolUsageFixBase(element, replaceWith) {
    override fun getFamilyName() = KotlinBundle.message("replace.deprecated.symbol.usage.in.whole.project")
    override fun getText() = text
    override fun startInWriteAction() = false

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        return super.isAvailable(project, editor, file) && targetPsiElement() != null
    }

    override fun invoke(replacementStrategy: UsageReplacementStrategy, project: Project, editor: Editor?) {
        val psiElement = targetPsiElement()!!
        replacementStrategy.replaceUsagesInWholeProject(
            psiElement,
            progressTitle = KotlinBundle.message("applying.0", text),
            commandName = text,
            unwrapSpecialUsages = false,
            unwrapper = { fullyExpandCall(it) }
        )
    }

    private fun targetPsiElement(): KtDeclaration? = when (val referenceTarget = element?.mainReference?.resolve()) {
        is KtNamedFunction -> referenceTarget
        is KtProperty -> referenceTarget
        is KtTypeAlias -> referenceTarget
        is KtConstructor<*> -> referenceTarget.getContainingClassOrObject() //TODO: constructor can be deprecated itself
        is KtClass -> referenceTarget.takeIf { it.isAnnotation() }
        else -> null
    }
}