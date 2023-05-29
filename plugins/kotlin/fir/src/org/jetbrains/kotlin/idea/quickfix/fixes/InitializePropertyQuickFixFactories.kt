// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactories
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

object InitializePropertyQuickFixFactories {

    data class AddInitializerInput(val initializerText: String?) : KotlinApplicatorInput

    private val addInitializerApplicator: KotlinApplicator<KtProperty, AddInitializerInput> = applicator {
        familyAndActionName(KotlinBundle.lazyMessage("add.initializer"))

        applyToWithEditorRequired { property, input, project, editor ->
            val initializer = property.setInitializer(KtPsiFactory(project).createExpression(input.initializerText ?: "TODO()"))!!
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
            editor.selectionModel.setSelection(initializer.startOffset, initializer.endOffset)
            editor.caretModel.moveToOffset(initializer.endOffset)
        }
    }


    val initializePropertyFactory =
        diagnosticFixFactories(
            KtFirDiagnostic.MustBeInitialized::class,
            KtFirDiagnostic.MustBeInitializedOrBeAbstract::class
        ) { diagnostic ->
            val property: KtProperty = diagnostic.psi

            // An extension property cannot be initialized because it has no backing field
            if (property.receiverTypeReference != null) return@diagnosticFixFactories emptyList()

            buildList {
                add(
                    KotlinApplicatorBasedQuickFix(
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
