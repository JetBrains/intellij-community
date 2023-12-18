/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.containers.toMutableSmartList
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.withInput
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.renderer.render

object SpecifySuperTypeFixFactory {

    class TypeStringWithoutArgs(
        val longTypeRepresentation: String,
        @NlsSafe val shortTypeRepresentation: String
    )

    class Input(val superTypes: List<TypeStringWithoutArgs>) : KotlinApplicatorInput

    private val applicator = applicator<KtSuperExpression, Input> {
        familyAndActionName(KotlinBundle.lazyMessage("intention.name.specify.supertype"))
        applyToWithEditorRequired { psi, input, project, editor ->
            when (input.superTypes.size) {
                0 -> return@applyToWithEditorRequired
                1 -> psi.specifySuperType(input.superTypes.single())
                else -> JBPopupFactory
                    .getInstance()
                    .createListPopup(createListPopupStep(psi, input.superTypes))
                    .showInBestPositionFor(editor)
            }
        }
    }

    private fun KtSuperExpression.specifySuperType(superType: TypeStringWithoutArgs) {
        project.executeWriteCommand(KotlinBundle.message("name.specify.supertype.command.title")) {
            val label = this.labelQualifier?.text ?: ""
            val psiFactory = KtPsiFactory(project)
            val replaced = replace(psiFactory.createExpression("super<${superType.longTypeRepresentation}>$label")) as KtSuperExpression
            shortenReferences(replaced)
        }
    }

    private fun createListPopupStep(superExpression: KtSuperExpression, superTypes: List<TypeStringWithoutArgs>): ListPopupStep<*> {
        return object : BaseListPopupStep<TypeStringWithoutArgs>(KotlinBundle.getMessage("popup.title.choose.supertype"), superTypes) {
            override fun isAutoSelectionEnabled() = false

            override fun onChosen(selectedValue: TypeStringWithoutArgs, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    superExpression.specifySuperType(selectedValue)
                }
                return PopupStep.FINAL_CHOICE
            }

            override fun getTextFor(value: TypeStringWithoutArgs): String {
                return value.shortTypeRepresentation
            }
        }
    }

    val ambiguousSuper = diagnosticFixFactory(KtFirDiagnostic.AmbiguousSuper::class, applicator) { diagnostic ->
        val candidates = diagnostic.candidates.toMutableSmartList()
        // TODO: the following logic would become unnecessary if feature https://youtrack.jetbrains.com/issue/KT-49314 is accepted because
        //  the candidate would not contain those being removed here.
        candidates.removeAll { superType ->
            candidates.any { otherSuperType ->
                !superType.isEqualTo(otherSuperType) && otherSuperType isSubTypeOf superType
            }
        }
        if (candidates.isEmpty()) return@diagnosticFixFactory listOf()
        val superTypes = candidates.mapNotNull { superType ->
            when (superType) {
                is KtErrorType -> null
                is KtNonErrorClassType ->
                    TypeStringWithoutArgs(superType.classId.asSingleFqName().render(), superType.classId.shortClassName.render())

                else -> error("Expected KtClassType but ${superType::class} was found")
            }
        }
        listOf(diagnostic.psi withInput Input(superTypes))
    }
}