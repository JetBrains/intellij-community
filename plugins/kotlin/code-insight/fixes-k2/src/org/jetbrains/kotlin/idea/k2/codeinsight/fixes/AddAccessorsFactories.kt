// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.fixes.AbstractKotlinApplicableQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAccessorUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAccessorUtils.addAccessors
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty

object AddAccessorsFactories {

    val addAccessorsToUninitializedProperty =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.MustBeInitialized ->
            createQuickFix(diagnostic.psi)
        }

    val addAccessorsToUninitializedOrAbstractProperty =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.MustBeInitializedOrBeAbstract ->
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
        target: KtProperty,
        private val addGetter: Boolean,
        private val addSetter: Boolean,
    ) : AbstractKotlinApplicableQuickFix<KtProperty>(target) {
        override fun getFamilyName(): String = AddAccessorUtils.familyAndActionName(addGetter, addSetter)

        override fun apply(element: KtProperty, project: Project, editor: Editor?, file: KtFile) {
            addAccessors(element, addGetter, addSetter) { editor?.moveCaret(it) }
        }
    }
}