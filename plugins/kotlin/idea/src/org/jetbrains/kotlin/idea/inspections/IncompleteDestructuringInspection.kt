/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.core.KotlinNameSuggester
import org.jetbrains.kotlin.idea.core.NewDeclarationNameValidator
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.destructuringDeclarationVisitor
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class IncompleteDestructuringInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return destructuringDeclarationVisitor(fun(destructuringDeclaration) {
            val primaryParameters = destructuringDeclaration.primaryParameters() ?: return
            if (destructuringDeclaration.entries.size < primaryParameters.size) {
                val rPar = destructuringDeclaration.rPar ?: return
                holder.registerProblem(
                    rPar,
                    KotlinBundle.message("incomplete.destructuring.declaration.text"),
                    IncompleteDestructuringQuickfix()
                )
            }
        })
    }
}

private fun KtDestructuringDeclaration.primaryParameters(): List<ValueParameterDescriptor>? {
    val type = initializer?.getType(analyze(BodyResolveMode.PARTIAL)) ?: return null
    val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return null
    return classDescriptor.constructors.firstOrNull { it.isPrimary }?.valueParameters
}

class IncompleteDestructuringQuickfix : LocalQuickFix {
    override fun getFamilyName() = KotlinBundle.message("incomplete.destructuring.fix.family.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val destructuringDeclaration = element.parent as? KtDestructuringDeclaration ?: return
        val primaryParameters = destructuringDeclaration.primaryParameters() ?: return

        val nameValidator = CollectingNameValidator(
            filter = NewDeclarationNameValidator(
                destructuringDeclaration.parent,
                null,
                NewDeclarationNameValidator.Target.VARIABLES
            )
        )
        val psiFactory = KtPsiFactory(destructuringDeclaration)
        val currentEntries = destructuringDeclaration.entries
        val hasType = currentEntries.any { it.typeReference != null }
        val additionalEntries = primaryParameters
            .drop(currentEntries.size)
            .map {
                val name = KotlinNameSuggester.suggestNameByName(it.name.asString(), nameValidator)
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
