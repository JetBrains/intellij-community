// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.*
import com.intellij.modcommand.ModCommand.chooseAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.containers.toMutableSmartList
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.renderer.render

object SpecifySuperTypeFixFactory {

    class TypeStringWithoutArgs(
        val longTypeRepresentation: String,
        @NlsSafe val shortTypeRepresentation: String
    )

    private class SpecifySuperTypeFix(val typeStringWithoutArgs: TypeStringWithoutArgs) :
        PsiUpdateModCommandAction<KtSuperExpression>(KtSuperExpression::class.java) {

        override fun getFamilyName(): @IntentionFamilyName String = typeStringWithoutArgs.shortTypeRepresentation

        override fun invoke(
            actionContext: ActionContext,
            element: KtSuperExpression,
            updater: ModPsiUpdater
        ) {
            val label = element.labelQualifier?.text ?: ""
            val psiFactory = KtPsiFactory(element.project)
            val newElement = psiFactory.createExpression("super<${typeStringWithoutArgs.longTypeRepresentation}>$label")
            val replaced = element.replace(newElement) as KtSuperExpression
            shortenReferences(replaced)
        }
    }

    private class SpecifySuperTypeQuickFix(
        element: KtSuperExpression,
        superTypes: List<TypeStringWithoutArgs>,
    ) : PsiBasedModCommandAction<KtSuperExpression>(element) {

        private val modCommands = superTypes.map { SpecifySuperTypeFix(it) }

        override fun getFamilyName(): String = KotlinBundle.message("intention.name.specify.supertype")

        override fun perform(
            context: ActionContext,
            element: KtSuperExpression
        ): ModCommand = chooseAction(KotlinBundle.message("intention.name.specify.supertype.title"), modCommands)
    }

    val ambiguousSuper: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.AmbiguousSuper> = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AmbiguousSuper ->
        val candidates = diagnostic.candidates.toMutableSmartList()
        // TODO: the following logic would become unnecessary if feature https://youtrack.jetbrains.com/issue/KT-49314 is accepted because
        //  the candidate would not contain those being removed here.
        candidates.removeAll { superType ->
            candidates.any { otherSuperType ->
                !superType.semanticallyEquals(otherSuperType) && otherSuperType.isSubtypeOf(superType)
            }
        }
        if (candidates.isEmpty()) return@ModCommandBased emptyList()
        val superTypes = candidates.mapNotNull { superType ->
            when (superType) {
                is KaErrorType -> null
                is KaClassType ->
                    TypeStringWithoutArgs(superType.classId.asSingleFqName().render(), superType.classId.shortClassName.render())

                else -> error("Expected a class or an error type, but ${superType::class} was found")
            }
        }

        if (superTypes.isEmpty()) return@ModCommandBased emptyList()

        listOf(SpecifySuperTypeQuickFix(diagnostic.psi, superTypes))
    }
}