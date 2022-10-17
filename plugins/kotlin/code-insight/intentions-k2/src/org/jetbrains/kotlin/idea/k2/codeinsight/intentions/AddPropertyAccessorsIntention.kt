// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.AddAccessorApplicator
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier

internal abstract class AbstractAddAccessorIntention(private val addGetter: Boolean, private val addSetter: Boolean) :
    AbstractKotlinApplicatorBasedIntention<KtProperty, KotlinApplicatorInput.Empty>(KtProperty::class) {
    override fun getApplicator() =
        AddAccessorApplicator.applicator(addGetter, addSetter).with {
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
        }

    override fun getApplicabilityRange() = applicabilityTarget { ktProperty: KtProperty ->
        if (ktProperty.hasInitializer()) ktProperty.nameIdentifier else ktProperty
    }

    override fun getInputProvider(): KotlinApplicatorInputProvider<KtProperty, KotlinApplicatorInput.Empty> = inputProvider { ktProperty ->
        val symbol = ktProperty.getVariableSymbol() as? KtPropertySymbol ?: return@inputProvider null
        if (symbol.hasAnnotation(JVM_FIELD_CLASS_ID)) return@inputProvider null

        KotlinApplicatorInput.Empty
    }
}

private val JVM_FIELD_CLASS_ID = ClassId.topLevel(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME)


internal class AddPropertyAccessorsIntention : AbstractAddAccessorIntention(addGetter = true, addSetter = true), LowPriorityAction
internal class AddPropertyGetterIntention : AbstractAddAccessorIntention(addGetter = true, addSetter = false)
internal class AddPropertySetterIntention : AbstractAddAccessorIntention(addGetter = false, addSetter = true)