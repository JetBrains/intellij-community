// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

object InitializePropertyQuickFixFactories {

    private data class ElementContext(
        val initializerText: String,
    )

    private class InitializePropertyModCommandAction(
        element: KtProperty,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtProperty, ElementContext>(element, elementContext) {

        override fun getFamilyName(): String = KotlinBundle.message("add.initializer")

        override fun invoke(
            actionContext: ActionContext,
            element: KtProperty,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val expression = KtPsiFactory(actionContext.project)
                .createExpression(elementContext.initializerText)
            val initializer = element.setInitializer(expression)!!
            updater.select(TextRange(initializer.startOffset, initializer.endOffset))
            updater.moveCaretTo(initializer.endOffset)
        }
    }

    // todo refactor
    val mustBeInitialized = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MustBeInitialized ->
        createFixes(diagnostic.psi)
    }

    val mustBeInitializedWarning = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MustBeInitializedWarning ->
        createFixes(diagnostic.psi)
    }

    val mustBeInitializedOrBeFinal = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MustBeInitializedOrBeFinal ->
        createFixes(diagnostic.psi)
    }

    val mustBeInitializedOrBeFinalWarning =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MustBeInitializedOrBeFinalWarning ->
            createFixes(diagnostic.psi)
        }

    val mustBeInitializedOrBeAbstract = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MustBeInitializedOrBeAbstract ->
        createFixes(diagnostic.psi)
    }

    val mustBeInitializedOrBeAbstractWarning =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MustBeInitializedOrBeAbstractWarning ->
            createFixes(diagnostic.psi)
        }

    val mustBeInitializedOrFinalOrAbstract =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MustBeInitializedOrFinalOrAbstract ->
            createFixes(diagnostic.psi)
        }

    val mustBeInitializedOrFinalOrAbstractWarning =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MustBeInitializedOrFinalOrAbstractWarning ->
            createFixes(diagnostic.psi)
        }

    context(KaSession)
    private fun createFixes(
        element: KtProperty,
    ): List<KotlinPsiUpdateModCommandAction<KtProperty, *>> {
        // An extension property cannot be initialized because it has no backing field
        if (element.receiverTypeReference != null) return emptyList()

        return buildList {
            val elementContext = ElementContext(
                initializerText = element.getReturnKtType().defaultInitializer ?: "TODO()",
            )
            add(InitializePropertyModCommandAction(element, elementContext))

            (element.containingClassOrObject as? KtClass)?.let { ktClass ->
                if (ktClass.isAnnotation() || ktClass.isInterface()) return@let

                // TODO: Add quickfixes MoveToConstructorParameters and InitializeWithConstructorParameter after change signature
                //  refactoring is available. See org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory
            }
        }
    }
}
