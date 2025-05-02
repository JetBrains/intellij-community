// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.utils.getJvmAnnotations
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class AddConstModifierFix(element: KtProperty) : AddModifierFix(element, KtTokens.CONST_KEYWORD), CleanupFix.ModCommand {

    private val propertyPointer: SmartPsiElementPointer<KtProperty> = element.createSmartPointer()

    override fun invoke(context: ActionContext, element: KtModifierListOwner, updater: ModPsiUpdater) {
        val property = element as? KtProperty ?: return
        val physicalProperty = propertyPointer.element ?: return
        replaceReferencesToGetterByReferenceToField(physicalProperty, updater)
        applyConstModifier(property)
    }

    companion object {
        fun addConstModifier(property: KtProperty) {
            replaceReferencesToGetterByReferenceToField(property, updater = null)
            applyConstModifier(property)
        }
    }
}

private fun applyConstModifier(property: KtProperty) {
    val annotations = property.getJvmAnnotations()
    property.addModifier(KtTokens.CONST_KEYWORD)
    annotations.forEach(KtAnnotationEntry::delete)
}

private fun replaceReferencesToGetterByReferenceToField(
    property: KtProperty,
    updater: ModPsiUpdater?,
) {
    val project = property.project
    val javaScope = GlobalSearchScope.getScopeRestrictedByFileTypes(project.allScope(), JavaFileType.INSTANCE)
    if (javaScope == GlobalSearchScope.EMPTY_SCOPE) return
    val getter = LightClassUtil.getLightClassPropertyMethods(property).getter ?: return
    val backingField = LightClassUtil.getLightClassPropertyMethods(property).backingField

    if (backingField != null) {
        val getterUsages = ReferencesSearch.search(getter, javaScope)
            .asIterable()
            .mapNotNull { updater?.getWritable(it.element) }
        if (getterUsages.isEmpty()) return
        val factory = PsiElementFactory.getInstance(project)
        val fieldFQName = backingField.containingClass!!.qualifiedName + "." + backingField.name

        getterUsages.forEach {
            val call = it.getNonStrictParentOfType<PsiMethodCallExpression>()
            if (call != null && it == call.methodExpression) {
                val fieldRef = factory.createExpressionFromText(fieldFQName, it)
                call.replace(fieldRef)
            }
        }
    }
}
