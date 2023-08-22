// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.search.PsiElementProcessorAdapter
import com.intellij.util.AdapterProcessor
import com.intellij.util.CommonProcessors
import com.intellij.util.Function
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.search.declarationsSearch.forEachOverridingMethod
import org.jetbrains.kotlin.idea.search.declarationsSearch.toPossiblyFakeLightMethods
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

fun getOverriddenPropertyTooltip(property: KtNamedDeclaration): String? {
    val overriddenInClassesProcessor = PsiElementProcessor.CollectElementsWithLimit<PsiClass>(5)

    val consumer = AdapterProcessor<PsiMethod, PsiClass>(
        CommonProcessors.UniqueProcessor(PsiElementProcessorAdapter(overriddenInClassesProcessor)),
        Function { method: PsiMethod? -> method?.containingClass }
    )

    for (method in property.toPossiblyFakeLightMethods()) {
        if (!overriddenInClassesProcessor.isOverflow) {
            method.forEachOverridingMethod(processor = consumer::process)
        }
    }

    val isImplemented = isImplemented(property)
    if (overriddenInClassesProcessor.isOverflow) {
        return if (isImplemented)
            KotlinBundle.message("overridden.marker.implementations.multiple")
        else
            KotlinBundle.message("overridden.marker.overrides.multiple")
    }

    val collectedClasses = overriddenInClassesProcessor.collection
    if (collectedClasses.isEmpty()) return null

    val start = if (isImplemented)
        KotlinBundle.message("overridden.marker.implementation")
    else
        KotlinBundle.message("overridden.marker.overrides")

    return KotlinGutterTooltipHelper.buildTooltipText(
        collectedClasses.sortedWith(PsiClassListCellRenderer().comparator),
        start, true, IdeActions.ACTION_GOTO_IMPLEMENTATION
    )
}


fun isImplemented(declaration: KtNamedDeclaration): Boolean {
    if (declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return true

    var parent = declaration.parent
    parent = if (parent is KtClassBody) parent.getParent() else parent

    if (parent !is KtClass) return false

    return parent.isInterface() && (declaration !is KtDeclarationWithBody || !declaration.hasBody()) && (declaration !is KtDeclarationWithInitializer || !declaration.hasInitializer())
}

