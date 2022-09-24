// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.destructuringDeclarationVisitor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class IncompleteDestructuringInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return destructuringDeclarationVisitor(fun(destructuringDeclaration) {
            val lPar = destructuringDeclaration.lPar ?: return
            val rPar = destructuringDeclaration.rPar ?: return
            val primaryParameters = destructuringDeclaration.primaryParameters() ?: return
            if (destructuringDeclaration.entries.size < primaryParameters.size) {
                val highlightRange =
                    TextRange(lPar.textRangeIn(destructuringDeclaration).startOffset, rPar.textRangeIn(destructuringDeclaration).endOffset)
                holder.registerProblem(
                    destructuringDeclaration,
                    highlightRange,
                    KotlinBundle.message("incomplete.destructuring.declaration.text"),
                    IncompleteDestructuringQuickfix()
                )
            }
        })
    }
}

private fun KtDestructuringDeclaration.primaryParameters(): List<ValueParameterDescriptor>? {
    val initializer = this.initializer
    val parameter = this.parent as? KtParameter
    val type = when {
        initializer != null -> initializer.getType(analyze(BodyResolveMode.PARTIAL))
        parameter != null -> analyze(BodyResolveMode.PARTIAL)[BindingContext.VALUE_PARAMETER, parameter]?.type
        else -> null
    } ?: return null
    val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return null
    return classDescriptor.constructors.firstOrNull { it.isPrimary }?.valueParameters
}

class IncompleteDestructuringQuickfix : LocalQuickFix {
    override fun getFamilyName() = KotlinBundle.message("incomplete.destructuring.fix.family.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val destructuringDeclaration = descriptor.psiElement as? KtDestructuringDeclaration ?: return
        addMissingEntries(destructuringDeclaration)
    }

    companion object {
        fun addMissingEntries(destructuringDeclaration: KtDestructuringDeclaration) {
            val primaryParameters = destructuringDeclaration.primaryParameters() ?: return

            val nameValidator = CollectingNameValidator(
                filter = Fe10KotlinNewDeclarationNameValidator(
                    destructuringDeclaration.parent,
                    null,
                    KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER
                )
            )
            val psiFactory = KtPsiFactory(destructuringDeclaration)
            val currentEntries = destructuringDeclaration.entries
            val hasType = currentEntries.any { it.typeReference != null }
            val additionalEntries = primaryParameters
                .drop(currentEntries.size)
                .map {
                    val name = Fe10KotlinNameSuggester.suggestNameByName(it.name.asString(), nameValidator)
                    if (hasType) {
                        val type = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(it.type)
                        "$name: $type"
                    } else {
                        name
                    }
                }
                .let { psiFactory.createDestructuringDeclaration("val (${it.joinToString()}) = TODO()").entries }

            val rPar = destructuringDeclaration.rPar
            val hasTrailingComma = destructuringDeclaration.trailingComma != null
            val currentEntriesIsEmpty = currentEntries.isEmpty()
            additionalEntries.forEachIndexed { index, entry ->
                if (index != 0 || (!hasTrailingComma && !currentEntriesIsEmpty)) {
                    destructuringDeclaration.addBefore(psiFactory.createComma(), rPar)
                }
                destructuringDeclaration.addBefore(entry, rPar)
            }
        }
    }
}
