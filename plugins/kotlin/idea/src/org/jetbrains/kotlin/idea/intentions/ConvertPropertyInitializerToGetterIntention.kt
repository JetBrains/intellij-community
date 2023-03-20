// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.hasJvmFieldAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.isError

class ConvertPropertyInitializerToGetterIntention : SelfTargetingRangeIntention<KtProperty>(
    KtProperty::class.java, KotlinBundle.lazyMessage("convert.property.initializer.to.getter")
) {
    override fun applicabilityRange(element: KtProperty): TextRange? {
        val initializer = element.initializer ?: return null
        val nameIdentifier = element.nameIdentifier ?: return null
        if (element.getter != null ||
            element.isExtensionDeclaration() ||
            element.isLocal ||
            element.hasJvmFieldAnnotation() ||
            element.hasModifier(KtTokens.CONST_KEYWORD)
        ) return null

        if (initializer.hasReferenceToPrimaryConstructorParameter()) return null

        return TextRange(nameIdentifier.startOffset, initializer.endOffset)
    }

    private fun KtExpression.hasReferenceToPrimaryConstructorParameter(): Boolean {
        val primaryConstructorParameters = containingClass()?.primaryConstructor?.valueParameters.orEmpty()
            .filterNot { it.hasValOrVar() }.associateBy { it.name }.ifEmpty { return false }

        return anyDescendantOfType<KtNameReferenceExpression> {
            val parameter = primaryConstructorParameters[it.text]
            parameter != null && parameter == it.mainReference.resolve()
        }
    }

    override fun skipProcessingFurtherElementsAfter(element: PsiElement): Boolean {
        // do not work inside lambda's in initializer - they can be too big
        return element is KtDeclaration || super.skipProcessingFurtherElementsAfter(element)
    }

    override fun applyTo(element: KtProperty, editor: Editor?) {
        convertPropertyInitializerToGetter(element, editor)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction {
            return ConvertPropertyInitializerToGetterIntention()
        }

        fun convertPropertyInitializerToGetter(property: KtProperty, editor: Editor?) {
            val psiFactory = KtPsiFactory(property.project)

            val initializer = property.initializer!!
            val getter = psiFactory.createPropertyGetter(initializer)
            val setter = property.setter

            when {
                setter != null -> property.addBefore(getter, setter)
                property.isVar -> {
                    property.add(getter)
                    val notImplemented = psiFactory.createExpression("TODO()")
                    val notImplementedSetter = psiFactory.createPropertySetter(notImplemented)
                    property.add(notImplementedSetter)
                }

                else -> property.add(getter)
            }

            property.initializer = null

            val type = SpecifyTypeExplicitlyIntention.getTypeForDeclaration(property)
            if (!type.isError) {
                SpecifyTypeExplicitlyIntention.addTypeAnnotation(editor, property, type)
            }
        }
    }
}
