// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAccessorUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAccessorUtils.addAccessors
import org.jetbrains.kotlin.psi.KtProperty

object AddAccessorsFactories {

    val addAccessorsToUninitializedProperty =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MustBeInitialized ->
            createQuickFix(diagnostic.psi)
        }

    val addAccessorsToUninitializedOrAbstractProperty =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MustBeInitializedOrBeAbstract ->
            createQuickFix(diagnostic.psi)
        }

    private fun createQuickFix(
        property: KtProperty,
    ): List<AddAccessorsQuickFix> {
        val addGetter = property.getter == null
        val addSetter = property.isVar && property.setter == null
        if (!addGetter && !addSetter) return emptyList()

        return listOf(
            AddAccessorsQuickFix(property, addGetter, addSetter),
        )
    }

    private class AddAccessorsQuickFix(
        element: KtProperty,
        private val addGetter: Boolean,
        private val addSetter: Boolean,
    ) : PsiUpdateModCommandAction<KtProperty>(element) {
        override fun getFamilyName(): String = AddAccessorUtils.familyAndActionName(addGetter, addSetter)

        override fun invoke(
            context: ActionContext,
            element: KtProperty,
            updater: ModPsiUpdater,
        ) {
            addAccessors(element, addGetter, addSetter, updater::moveCaretTo)
        }
    }
}