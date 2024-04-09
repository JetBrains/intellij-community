// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.doesDataClassCopyRespectConstructorVisibility
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.primaryConstructorVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

internal class DataClassPrivateConstructorInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (holder.file.languageVersionSettings.supportsFeature(LanguageFeature.ErrorAboutDataClassCopyVisibilityChange) ||
            holder.file.languageVersionSettings.doesDataClassCopyRespectConstructorVisibility()
        ) {
            // DataClassPrivateConstructorInspection inspection is redundant. We have a compiler diagnostic instead.
            // Ideally, we should also disable the inspection when the warning is reported,
            // but we don't have a separate LanguageFeature enum entry for the warning.
            return PsiElementVisitor.EMPTY_VISITOR
        }
        return primaryConstructorVisitor { constructor ->
            val containingClass = constructor.containingClass()
            if (containingClass?.isData() == true && constructor.isPrivate()) {
                val isAnnotated = containingClass.annotationEntries.any {
                    // A more correct solution is to resolve the annotation to see if it's FQN matches FQNs of annotations from stdlib.
                    // But it's too much hassle. This inspection will be eventually dropped anyway.
                    // "ConsistentCopyVisibility" and "ExposedCopyVisibility" are unique enough names.
                    // And false negatives are not a big deal in case of annotation name collision
                    it.shortName == StandardClassIds.Annotations.ConsistentCopyVisibility.shortClassName ||
                            it.shortName == StandardClassIds.Annotations.ExposedCopyVisibility.shortClassName
                }
                if (isAnnotated) {
                    return@primaryConstructorVisitor
                }
                val keyword = constructor.modifierList?.getModifier(KtTokens.PRIVATE_KEYWORD) ?: return@primaryConstructorVisitor
                val problemDescriptor = holder.manager.createProblemDescriptor(
                    keyword,
                    keyword,
                    KotlinBundle.message("private.data.class.constructor.is.exposed.via.the.generated.copy.method"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    isOnTheFly
                )

                holder.registerProblem(problemDescriptor)
            }
        }
    }
}