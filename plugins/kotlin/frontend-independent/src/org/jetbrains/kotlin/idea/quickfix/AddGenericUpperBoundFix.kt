// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeParameter

class AddGenericUpperBoundFix(
    typeParameter: KtTypeParameter,
    private val renderedUpperBound: String,
) : KotlinQuickFixAction<KtTypeParameter>(typeParameter) {

    override fun getText(): String {
        val element = this.element
        return when {
            element != null -> KotlinBundle.message("fix.add.generic.upperbound.text", renderedUpperBound, element.name.toString())
            else -> null
        } ?: ""
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.generic.upperbound.family")

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val element = element ?: return false
        // TODO: replacing existing upper bounds
        return (element.name != null && element.extendsBound == null)
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        assert(element.extendsBound == null) { "Don't know what to do with existing bounds" }

        val typeReference = KtPsiFactory(project).createType(renderedUpperBound)
        val insertedTypeReference = element.setExtendsBound(typeReference)!!

        ShortenReferencesFacility.getInstance().shorten(insertedTypeReference)
    }
}
