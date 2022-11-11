// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.impl.MarkerType
import com.intellij.java.JavaBundle
import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFunctionalExpression
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.search.PsiElementProcessorAdapter
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import org.jetbrains.kotlin.idea.presentation.DeclarationByModuleRenderer
import org.jetbrains.kotlin.utils.addIfNotNull
import java.awt.event.MouseEvent
import javax.swing.JComponent

fun buildNavigateToClassInheritorsPopup(e: MouseEvent?, element: PsiElement?): NavigationPopupDescriptor? {
    val psiElement = element ?: return null
    val project = psiElement.project
    if (DumbService.isDumb(project)) {
        DumbService.getInstance(project).showDumbModeNotification(
            JavaBundle.message("notification.navigation.to.overriding.methods")
        )
        return null
    }

    val psiClass = getPsiClass(psiElement) ?: return null
    val renderer = DeclarationByModuleRenderer()

    val collectProcessor = PsiElementProcessor.FindElement<PsiClass>()
    val collectExprProcessor = PsiElementProcessor.FindElement<PsiFunctionalExpression>()
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                ClassInheritorsSearch.search(psiClass).forEach(PsiElementProcessorAdapter(collectProcessor))
                if (collectProcessor.foundElement == null) {
                    FunctionalExpressionSearch.search(psiClass).forEach(PsiElementProcessorAdapter(collectExprProcessor))
                }
            },
            JavaAnalysisBundle.message("progress.title.searching.for.overridden.methods"),
            true,
            project,
            e?.component as? JComponent
        )
    ) {
        return null
    }

    val inheritors = mutableListOf<NavigatablePsiElement>()
    inheritors.addIfNotNull(collectProcessor.foundElement)
    inheritors.addIfNotNull(collectExprProcessor.foundElement)
    if (inheritors.isEmpty()) return null

    inheritors.sortWith(renderer.comparator)

    val updater = MarkerType.SubclassUpdater(psiClass, renderer)

    val className = psiClass.name
    return NavigationPopupDescriptor(
        inheritors,
        updater.getCaption(inheritors.size)!!,
        CodeInsightBundle.message("goto.implementation.findUsages.title", className),
        renderer,
        updater
    )
}

