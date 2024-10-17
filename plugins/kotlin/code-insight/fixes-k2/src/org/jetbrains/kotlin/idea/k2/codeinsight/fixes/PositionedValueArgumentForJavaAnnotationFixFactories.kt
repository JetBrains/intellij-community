// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal object PositionedValueArgumentForJavaAnnotationFixFactories {

    private data class ValueArgument(
        val expression: SmartPsiElementPointer<KtExpression>,
        val name: Name,
    )

    val replaceWithNamedArgumentsFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.PositionedValueArgumentForJavaAnnotation ->
            listOfNotNull(createFixIfAvailable(diagnostic.psi))
        }

    private fun KaSession.createFixIfAvailable(
        element: KtElement,
    ): ReplaceWithNamedArgumentsFix? {
        val annotationEntry = element.parentOfType<KtAnnotationEntry>() ?: return null
        val resolvedCall = annotationEntry.resolveToCall()?.singleFunctionCallOrNull() ?: return null

        val valueArguments = resolvedCall
            .argumentMapping
            .filter { (expression, signature) -> mustArgumentBeNamed(expression, signature) }
            .map { (expr, signature) ->
                ValueArgument(expr.createSmartPointer(), signature.name)
            }

        return ReplaceWithNamedArgumentsFix(annotationEntry, valueArguments)
    }

    private fun mustArgumentBeNamed(
        expression: KtExpression,
        signature: KaVariableSignature<KaValueParameterSymbol>,
    ): Boolean {
        return signature.name != JvmAnnotationNames.DEFAULT_ANNOTATION_MEMBER_NAME
                && (expression.parent as? KtValueArgument)?.isNamed() != true
    }

    private class ReplaceWithNamedArgumentsFix(
        element: KtAnnotationEntry,
        private val valueArguments: List<ValueArgument>,
    ) : PsiUpdateModCommandAction<KtAnnotationEntry>(element), CleanupFix.ModCommand {

        override fun getFamilyName(): String = KotlinBundle.message("replace.invalid.positioned.arguments.for.annotation")

        override fun invoke(
            context: ActionContext,
            element: KtAnnotationEntry,
            updater: ModPsiUpdater,
        ) {
            val writableValueArguments = valueArguments.mapNotNull { it.expression.element }.map(updater::getWritable)
            val names = valueArguments.map { it.name }

            val factory = KtPsiFactory(element.project)

            writableValueArguments.zip(names).forEach { (expression, name) ->
                expression.replace(factory.createArgument(expression, name))
            }
        }
    }
}
