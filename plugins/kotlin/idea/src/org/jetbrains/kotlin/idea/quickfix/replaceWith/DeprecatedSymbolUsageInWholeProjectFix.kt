// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.replaceWith

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInliner.unwrapSpecialUsageOrNull
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.replaceUsagesInWholeProject
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy

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
            unwrapper = { unwrapSpecialUsageOrNull(it) }
        )
    }

    private fun targetPsiElement(): KtDeclaration? = when (val referenceTarget = element?.mainReference?.resolve()) {
        is KtNamedFunction -> referenceTarget
        is KtProperty -> referenceTarget
        is KtTypeAlias -> referenceTarget
        is KtConstructor<*> -> referenceTarget.getContainingClassOrObject() //TODO: constructor can be deprecated itself
        is KtClass -> referenceTarget
        else -> null
    }

    companion object : KotlinSingleIntentionActionFactory() {
        //TODO: better rendering needed
        private val RENDERER = DescriptorRenderer.withOptions {
            modifiers = emptySet()
            classifierNamePolicy = ClassifierNamePolicy.SHORT
            parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
            receiverAfterName = true
            renderCompanionObjectName = true
            withoutSuperTypes = true
            startFromName = true
            withDefinedIn = false
        }

        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val (referenceExpression, replacement, descriptor) = extractDataFromDiagnostic(diagnostic, true) ?: return null
            val descriptorName = RENDERER.render(descriptor)
            return DeprecatedSymbolUsageInWholeProjectFix(
                referenceExpression,
                replacement,
                KotlinBundle.message("replace.usages.of.0.in.whole.project", descriptorName)
            ).takeIf { it.isAvailable }
        }
    }
}
