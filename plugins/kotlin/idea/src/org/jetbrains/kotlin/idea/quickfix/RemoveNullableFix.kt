// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveNullableFix(
    element: KtNullableType,
    private val typeOfError: NullableKind
) : KotlinQuickFixAction<KtNullableType>(element) {
    enum class NullableKind(@Nls val message: String) {
        REDUNDANT(KotlinBundle.message("remove.redundant")),
        SUPERTYPE(KotlinBundle.message("text.remove.question")),
        USELESS(KotlinBundle.message("remove.useless")),
        PROPERTY(KotlinBundle.message("make.not.nullable"))
    }

    override fun getFamilyName() = KotlinBundle.message("text.remove.question")

    override fun getText() = typeOfError.message

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val type = element.innerType ?: error("No inner type " + element.text + ", should have been rejected in createFactory()")
        element.replace(type)
    }

    class Factory(private val typeOfError: NullableKind) : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtNullableType>? {
            val nullType = diagnostic.psiElement.getNonStrictParentOfType<KtNullableType>()
            if (nullType?.innerType == null) return null
            return RemoveNullableFix(nullType, typeOfError)
        }
    }

    object LATEINIT_FACTORY : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtNullableType>? {
            val property = Errors.INAPPLICABLE_LATEINIT_MODIFIER.cast(diagnostic).psiElement as? KtProperty ?: return null
            val typeReference = property.typeReference ?: return null
            val typeElement = (typeReference.typeElement ?: return null) as? KtNullableType ?: return null
            if (typeElement.innerType == null) return null
            return RemoveNullableFix(typeElement, NullableKind.PROPERTY)
        }
    }
}
