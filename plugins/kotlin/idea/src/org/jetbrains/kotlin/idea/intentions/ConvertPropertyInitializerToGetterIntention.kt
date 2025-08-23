// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.canConvertPropertyInitializerToGetterByPsi
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.convertPropertyInitializerToGetterInner
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.hasReferenceToPrimaryConstructorParameter
import org.jetbrains.kotlin.idea.intentions.ConvertPropertyInitializerToGetterIntention.Factory.convertPropertyInitializerToGetter
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.isError

class ConvertPropertyInitializerToGetterIntention : SelfTargetingRangeIntention<KtProperty>(
    KtProperty::class.java, KotlinBundle.messagePointer("convert.property.initializer.to.getter")
) {
    override fun applicabilityRange(element: KtProperty): TextRange? {
        val initializer = element.initializer ?: return null
        val nameIdentifier = element.nameIdentifier ?: return null
        if (!canConvertPropertyInitializerToGetterByPsi(element)) return null
        if (initializer.hasReferenceToPrimaryConstructorParameter()) return null

        return TextRange(nameIdentifier.startOffset, initializer.endOffset)
    }

    override fun skipProcessingFurtherElementsAfter(element: PsiElement): Boolean {
        // do not work inside lambda's in initializer - they can be too big
        return element is KtDeclaration || super.skipProcessingFurtherElementsAfter(element)
    }

    override fun applyTo(element: KtProperty, editor: Editor?) {
        convertPropertyInitializerToGetter(element, editor)
    }

    object Factory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction {
            return ConvertPropertyInitializerToGetterIntention()
        }

        fun convertPropertyInitializerToGetter(property: KtProperty, editor: Editor?) {
            convertPropertyInitializerToGetterInner(property) {
                val type = SpecifyTypeExplicitlyIntention.getTypeForDeclaration(property)
                if (!type.isError) {
                    SpecifyTypeExplicitlyIntention.addTypeAnnotation(editor, property, type)
                }
            }
        }
    }
}
