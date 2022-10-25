// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactories
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAccessorUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAccessorUtils.addAccessors
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty

object AddAccessorsFactories {
    val addAccessorsToUninitializedProperty =
        diagnosticFixFactories(
            KtFirDiagnostic.MustBeInitialized::class,
            KtFirDiagnostic.MustBeInitializedOrBeAbstract::class
        ) { diagnostic ->
            val property: KtProperty = diagnostic.psi
            val addGetter = property.getter == null
            val addSetter = property.isVar && property.setter == null
            if (!addGetter && !addSetter) return@diagnosticFixFactories emptyList()
            listOf(
                AddAccessorsQuickFix(property, addGetter, addSetter)
            )
        }

    private class AddAccessorsQuickFix(
        target: KtProperty,
        private val addGetter: Boolean,
        private val addSetter: Boolean,
    ) : KotlinApplicableQuickFix<KtProperty>(target) {
        override fun getFamilyName(): String = AddAccessorUtils.familyAndActionName(addGetter, addSetter)
        override fun apply(element: KtProperty, project: Project, editor: Editor?, file: KtFile) =
            addAccessors(element, addGetter, addSetter, editor)
    }
}