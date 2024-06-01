// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

abstract class ConvertLateinitPropertyToNotNullDelegateFixBase(
    property: KtProperty,
    private val type: String
) : KotlinQuickFixAction<KtProperty>(property) {
    override fun getText() = KotlinBundle.message("convert.to.notnull.delegate")

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val property = element ?: return
        val typeReference = property.typeReference ?: return
        val psiFactory = KtPsiFactory(project)
        property.removeModifier(KtTokens.LATEINIT_KEYWORD)
        val propertyDelegate = psiFactory.createPropertyDelegate(
            psiFactory.createExpression("kotlin.properties.Delegates.notNull<$type>()")
        )
        property.addAfter(propertyDelegate, typeReference)
        property.typeReference = null
        shortenReferences(property)
    }

    abstract fun shortenReferences(property: KtProperty)
}
