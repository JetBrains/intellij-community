// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.search.PsiElementProcessorAdapter
import com.intellij.util.AdapterProcessor
import com.intellij.util.CommonProcessors
import com.intellij.util.Function
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.search.declarationsSearch.forEachOverridingMethod
import org.jetbrains.kotlin.idea.search.declarationsSearch.toPossiblyFakeLightMethods
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinDefinitionsSearcher
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.awt.event.MouseEvent
import javax.swing.JComponent

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

    val pattern = "&nbsp;&nbsp;&nbsp;&nbsp;{0}"
    return com.intellij.codeInsight.daemon.impl.GutterIconTooltipHelper.composeText(
        collectedClasses.sortedWith(PsiClassListCellRenderer().comparator),
        start,
        pattern
    )
}

fun buildNavigateToPropertyOverriddenDeclarationsPopup(e: MouseEvent?, element: PsiElement?): NavigationPopupDescriptor? {
    val propertyOrParameter = element?.parent as? KtNamedDeclaration ?: return null
    val project = propertyOrParameter.project

    if (DumbService.isDumb(project)) {
        DumbService.getInstance(project)?.showDumbModeNotification(
            KotlinBundle.message("highlighter.notification.text.navigation.to.overriding.classes.is.not.possible.during.index.update")
        )
        return null
    }

    val psiPropertyMethods = propertyOrParameter.toPossiblyFakeLightMethods()
    val elementProcessor = CommonProcessors.CollectUniquesProcessor<PsiElement>()
    val ktPsiMethodProcessor = Runnable {
        KotlinDefinitionsSearcher.processPropertyImplementationsMethods(
            psiPropertyMethods,
            GlobalSearchScope.allScope(project),
            elementProcessor
        )
    }

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
            /* runnable */ ktPsiMethodProcessor,
                           KotlinBundle.message("searching.for.overriding.methods"),
            /* can be canceled */ true,
                           project,
                           e?.component as JComponent?
        )
    ) {
        return null
    }

    val renderer = DefaultPsiElementCellRenderer()
    val navigatingOverrides = elementProcessor.results
        .sortedWith(renderer.comparator)
        .filterIsInstance<NavigatablePsiElement>()

    return NavigationPopupDescriptor(
        navigatingOverrides,
        KotlinBundle.message("overridden.marker.implementations.choose.implementation.title", propertyOrParameter.name.toString()),
        KotlinBundle.message("overridden.marker.implementations.choose.implementation.find.usages", propertyOrParameter.name.toString()),
        renderer
    )
}


fun isImplemented(declaration: KtNamedDeclaration): Boolean {
    if (declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return true

    var parent = declaration.parent
    parent = if (parent is KtClassBody) parent.getParent() else parent

    if (parent !is KtClass) return false

    return parent.isInterface() && (declaration !is KtDeclarationWithBody || !declaration.hasBody()) && (declaration !is KtDeclarationWithInitializer || !declaration.hasInitializer())
}

