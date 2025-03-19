// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.StarProjectionUtils.starProjectionFixFamilyName
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier

internal object AddStarProjectionsFixFactory : KotlinSingleIntentionActionFactory() {
    public override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val diagnosticWithParameters = Errors.NO_TYPE_ARGUMENTS_ON_RHS.cast(diagnostic)
        val typeReference = diagnosticWithParameters.psiElement

        if (typeReference.classDescriptor()?.isInner == true)
            return AddStartProjectionsForInnerClass(typeReference)
        else {
            val typeElement = typeReference.typeElement ?: return null
            val unwrappedType = StarProjectionUtils.getUnwrappedType(typeElement) ?: return null
            return AddStarProjectionsFix(unwrappedType, diagnosticWithParameters.a).asIntention()
        }
    }
}

private class AddStartProjectionsForInnerClass(element: KtTypeReference) : KotlinQuickFixAction<KtTypeReference>(element) {

    override fun getFamilyName() = text

    override fun getText() = starProjectionFixFamilyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val typeReference = element ?: return
        val targetClasses = getTargetClasses(typeReference) ?: return
        val replaceString = createReplaceString(targetClasses)
        typeReference.replace(KtPsiFactory(project).createType(replaceString))
    }

    private fun getTargetClasses(typeReference: KtTypeReference): List<ClassDescriptor>? {
        val classDescriptor = typeReference.classDescriptor() ?: return null

        val parentWithSelfClasses = classDescriptor.parentsWithSelf.mapNotNull { it as? ClassDescriptor }.toList()

        val scope = typeReference.getResolutionScope()
        val targets = parentWithSelfClasses.takeWhile { it.isInner || !it.inScope(scope) }

        val last = targets.lastOrNull() ?: return targets
        val next = parentWithSelfClasses.getOrNull(targets.size) ?: return targets

        return if (last.isInner && next.declaredTypeParameters.isNotEmpty() || !last.inScope(scope)) {
            targets + next
        } else {
            targets
        }
    }

    private fun createReplaceString(targetClasses: List<ClassDescriptor>): String {
        return targetClasses.mapIndexed { index, c ->
            val name = c.name.asString()
            val last = targetClasses.getOrNull(index - 1)
            val size = if (index == 0 || last?.isInner == true) c.declaredTypeParameters.size else 0
            if (size == 0) name else StarProjectionUtils.getTypeNameAndStarProjectionsString(name, size)
        }.reversed().joinToString(".")
    }
}

private fun KtTypeReference.classDescriptor(): ClassDescriptor? =
    this.analyze()[BindingContext.TYPE, this]?.constructor?.declarationDescriptor as? ClassDescriptor

private fun ClassDescriptor.inScope(scope: LexicalScope): Boolean = scope.findClassifier(this.name, NoLookupLocation.FROM_IDE) != null
