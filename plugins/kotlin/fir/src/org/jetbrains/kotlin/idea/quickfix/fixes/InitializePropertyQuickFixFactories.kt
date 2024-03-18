// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinModCommandApplicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedModCommand
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticModCommandFixFactories
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.modCommandApplicator
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

object InitializePropertyQuickFixFactories {

    data class AddInitializerInput(val initializerText: String?) : KotlinApplicatorInput

    private val addInitializerApplicator: KotlinModCommandApplicator<KtProperty, AddInitializerInput> = modCommandApplicator {
        familyAndActionName(KotlinBundle.lazyMessage("add.initializer"))

        applyTo { property, input, context, updater ->
            val initializer = property.setInitializer(KtPsiFactory(context.project).createExpression(input.initializerText ?: "TODO()"))!!
            updater.select(TextRange(initializer.startOffset, initializer.endOffset))
            updater.moveCaretTo(initializer.endOffset)
        }
    }


    val initializePropertyFactory =
        diagnosticModCommandFixFactories(
            KtFirDiagnostic.MustBeInitialized::class,
            KtFirDiagnostic.MustBeInitializedWarning::class,
            KtFirDiagnostic.MustBeInitializedOrBeFinal::class,
            KtFirDiagnostic.MustBeInitializedOrBeFinalWarning::class,
            KtFirDiagnostic.MustBeInitializedOrBeAbstract::class,
            KtFirDiagnostic.MustBeInitializedOrBeAbstractWarning::class,
            KtFirDiagnostic.MustBeInitializedOrFinalOrAbstract::class,
            KtFirDiagnostic.MustBeInitializedOrFinalOrAbstractWarning::class,
        ) { diagnostic ->
            val property: KtProperty = diagnostic.psi

            // An extension property cannot be initialized because it has no backing field
            if (property.receiverTypeReference != null) return@diagnosticModCommandFixFactories emptyList()

            buildList {
                add(
                    KotlinApplicatorBasedModCommand(
                        property,
                        AddInitializerInput(property.getReturnKtType().defaultInitializer),
                        addInitializerApplicator
                    )
                )

                (property.containingClassOrObject as? KtClass)?.let { ktClass ->
                    if (ktClass.isAnnotation() || ktClass.isInterface()) return@let

                    // TODO: Add quickfixes MoveToConstructorParameters and InitializeWithConstructorParameter after change signature
                    //  refactoring is available. See org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory
                }
            }
        }
}
