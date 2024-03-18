// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableModCommandIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityTarget
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAccessorUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAccessorUtils.addAccessors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier

internal abstract class AbstractAddAccessorIntention(
    private val addGetter: Boolean,
    private val addSetter: Boolean,
) : AbstractKotlinApplicableModCommandIntention<KtProperty>(KtProperty::class) {
    override fun getFamilyName(): String = AddAccessorUtils.familyAndActionName(addGetter, addSetter)
    override fun getActionName(element: KtProperty): String = familyName

    override fun getApplicabilityRange() = applicabilityTarget { ktProperty: KtProperty ->
        if (ktProperty.hasInitializer()) ktProperty.nameIdentifier else ktProperty
    }

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (element.isLocal ||
            element.hasDelegate() ||
            element.containingClass()?.isInterface() == true ||
            element.containingClassOrObject?.hasExpectModifier() == true ||
            element.hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
            element.hasModifier(KtTokens.LATEINIT_KEYWORD) ||
            element.hasModifier(KtTokens.CONST_KEYWORD)
        ) {
            return false
        }

        if (element.typeReference == null && !element.hasInitializer()) return false
        if (addSetter && (!element.isVar || element.setter != null)) return false
        if (addGetter && element.getter != null) return false

        return true
    }

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtProperty): Boolean {
        if (element.annotationEntries.isEmpty()) return true
        val symbol = element.getVariableSymbol() as? KtPropertySymbol ?: return false
        return symbol.backingFieldSymbol?.hasAnnotation(JVM_FIELD_CLASS_ID) != true
    }

    override fun apply(element: KtProperty, context: ActionContext, updater: ModPsiUpdater) {
        addAccessors(element, addGetter, addSetter, updater::moveCaretTo)
    }
}

private val JVM_FIELD_CLASS_ID = ClassId.topLevel(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME)

internal class AddPropertyAccessorsIntention : AbstractAddAccessorIntention(addGetter = true, addSetter = true), LowPriorityAction
internal class AddPropertyGetterIntention : AbstractAddAccessorIntention(addGetter = true, addSetter = false)
internal class AddPropertySetterIntention : AbstractAddAccessorIntention(addGetter = false, addSetter = true)