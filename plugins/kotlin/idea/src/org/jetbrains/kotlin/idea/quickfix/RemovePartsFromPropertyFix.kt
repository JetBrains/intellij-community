// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.compilerPreferences.KotlinBaseCompilerConfigurationUiBundle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty

open class RemovePartsFromPropertyFix(
    element: KtProperty,
    private val removeInitializer: Boolean,
    private val removeGetter: Boolean,
    private val removeSetter: Boolean
) : KotlinQuickFixAction<KtProperty>(element) {
    override fun getText(): String {
        val chunks = ArrayList<String>(3).apply {
            if (removeGetter) add(KotlinBundle.message("text.getter"))
            if (removeSetter) add(KotlinBundle.message("text.setter"))
            if (removeInitializer) add(KotlinBundle.message("text.initializer"))
        }

        fun concat(head: String, tail: String): String {
            return head + " " + KotlinBaseCompilerConfigurationUiBundle.message("configuration.text.and") + " " + tail
        }

        val partsText = when (chunks.size) {
            0 -> ""
            1 -> chunks.single()
            2 -> concat(chunks[0], chunks[1])
            else -> concat(chunks.dropLast(1).joinToString(", "), chunks.last())
        }

        return KotlinBundle.message("remove.0.from.property", partsText)
    }

    override fun getFamilyName(): String = KotlinBundle.message("remove.parts.from.property")

    public override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = this.element ?: return

        if (removeInitializer) {
            val initializer = element.initializer
            if (initializer != null) {
                if (element.typeReference == null) {
                    val type = SpecifyTypeExplicitlyIntention.getTypeForDeclaration(element)
                    SpecifyTypeExplicitlyIntention.addTypeAnnotation(null, element, type)
                }

                element.deleteChildRange(element.equalsToken ?: initializer, initializer)
            }
        }

        if (removeGetter) {
            element.getter?.delete()
        }

        if (removeSetter) {
            element.setter?.delete()
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtProperty>? {
            val element = diagnostic.psiElement
            val property = PsiTreeUtil.getParentOfType(element, KtProperty::class.java) ?: return null
            return RemovePartsFromPropertyFix(
                property,
                removeInitializer = property.hasInitializer(),
                removeGetter = property.getter?.bodyExpression != null,
                removeSetter = property.setter?.bodyExpression != null
            )
        }
    }

    object LateInitFactory : KotlinSingleIntentionActionFactory() {
        public override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtProperty>? {
            val property = Errors.INAPPLICABLE_LATEINIT_MODIFIER.cast(diagnostic).psiElement as? KtProperty ?: return null
            val hasInitializer = property.hasInitializer()
            val hasGetter = property.getter?.bodyExpression != null
            val hasSetter = property.setter?.bodyExpression != null
            if (!hasInitializer && !hasGetter && !hasSetter) {
                return null
            }

            return RemovePartsFromPropertyFix(property, hasInitializer, hasGetter, hasSetter)
        }
    }

}
