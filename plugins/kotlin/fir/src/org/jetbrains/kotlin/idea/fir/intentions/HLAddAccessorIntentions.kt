// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import org.jetbrains.kotlin.analysis.api.annotations.containsAnnotation
import org.jetbrains.kotlin.idea.codeinsight.api.KotlinApplicator
import org.jetbrains.kotlin.idea.codeinsight.api.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicator
import org.jetbrains.kotlin.idea.codeinsight.api.AbstractKotlinApplicatorBasedIntention
import org.jetbrains.kotlin.idea.codeinsight.api.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.KotlinApplicatorInputProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicabilityTarget
import org.jetbrains.kotlin.idea.codeinsight.api.inputProvider
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.idea.intentions.AbstractAddAccessorsIntention
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier

abstract class HLAddAccessorIntention(private val addGetter: Boolean, private val addSetter: Boolean) :
    AbstractKotlinApplicatorBasedIntention<KtProperty, KotlinApplicatorInput.Empty>(KtProperty::class, applicator(addGetter, addSetter)) {
    override val applicabilityRange: KotlinApplicabilityRange<KtProperty> = applicabilityTarget { ktProperty ->
        if (ktProperty.hasInitializer()) ktProperty.nameIdentifier else ktProperty
    }

    override val inputProvider: KotlinApplicatorInputProvider<KtProperty, KotlinApplicatorInput.Empty> = inputProvider { ktProperty ->
        val symbol = ktProperty.getVariableSymbol() as? KtPropertySymbol ?: return@inputProvider null
        if (symbol.containsAnnotation(JVM_FIELD_CLASS_ID)) return@inputProvider null

        KotlinApplicatorInput
    }

    companion object {
        private val JVM_FIELD_CLASS_ID = ClassId.topLevel(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME)

        private fun applicator(addGetter: Boolean, addSetter: Boolean): KotlinApplicator<KtProperty, KotlinApplicatorInput.Empty> = applicator {
            familyAndActionName(AbstractAddAccessorsIntention.createFamilyName(addGetter, addSetter))

            isApplicableByPsi { ktProperty ->
                if (ktProperty.isLocal || ktProperty.hasDelegate() ||
                    ktProperty.containingClass()?.isInterface() == true ||
                    ktProperty.containingClassOrObject?.hasExpectModifier() == true ||
                    ktProperty.hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
                    ktProperty.hasModifier(KtTokens.LATEINIT_KEYWORD) ||
                    ktProperty.hasModifier(KtTokens.CONST_KEYWORD)
                ) {
                    return@isApplicableByPsi false
                }

                if (ktProperty.typeReference == null && !ktProperty.hasInitializer()) return@isApplicableByPsi false
                if (addSetter && (!ktProperty.isVar || ktProperty.setter != null)) return@isApplicableByPsi false
                if (addGetter && ktProperty.getter != null) return@isApplicableByPsi false

                true
            }

            applyTo { ktProperty, _, _, editor ->
                AbstractAddAccessorsIntention.applyTo(ktProperty, editor, addGetter, addSetter)
            }
        }
    }
}

class HLAddGetterAndSetterIntention : HLAddAccessorIntention(addGetter = true, addSetter = true), LowPriorityAction
class HLAddGetterIntention : HLAddAccessorIntention(addGetter = true, addSetter = false)
class HLAddSetterIntention : HLAddAccessorIntention(addGetter = false, addSetter = true)