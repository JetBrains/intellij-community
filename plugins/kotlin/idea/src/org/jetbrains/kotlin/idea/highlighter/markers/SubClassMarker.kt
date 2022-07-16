// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.navigation.BackgroundUpdaterTask
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.java.JavaBundle
import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.search.PsiElementProcessorAdapter
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.CommonProcessors.CollectProcessor
import com.intellij.util.Processor
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.presentation.DeclarationByModuleRenderer
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
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

    val collectProcessor = PsiElementProcessor.FindElement<PsiClass?>()
    val collectExprProcessor = PsiElementProcessor.FindElement<PsiFunctionalExpression>()
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                ClassInheritorsSearch.search(psiClass)
                    .forEach(PsiElementProcessorAdapter(collectProcessor))
                if (collectProcessor.foundElement == null) {
                    FunctionalExpressionSearch.search(psiClass)
                        .forEach(PsiElementProcessorAdapter(collectExprProcessor))
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

    val inheritors = mutableSetOf<NavigatablePsiElement>()
    inheritors.addIfNotNull(collectProcessor.foundElement)
    inheritors.addIfNotNull(collectExprProcessor.foundElement)
    if (inheritors.isEmpty()) return null

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                ClassInheritorsSearch.search(psiClass, runReadAction {
                    PsiSearchHelper.getInstance(project).getUseScope(psiClass)
                }, true).forEach(object : CollectProcessor<PsiClass>() {
                    override fun process(o: PsiClass): Boolean {
                        ProgressManager.checkCanceled()
                        inheritors.add(o)
                        return true
                    }
                })
            },
            JavaAnalysisBundle.message("progress.title.searching.for.overridden.methods"),
            true,
            project,
            e?.component as? JComponent
        )
    ) {
        return null
    }

    val inheritorList = inheritors.toMutableList()
    inheritorList.sortWith(renderer.comparator as Comparator<in NavigatablePsiElement>)

    val updater = SubclassUpdater(psiClass, renderer, inheritorList)

    val className = psiClass.name
    return NavigationPopupDescriptor(
        inheritorList,
        updater.getCaption(inheritors.size),
        CodeInsightBundle.message("goto.implementation.findUsages.title", className),
        renderer,
        updater
    )
}


private fun comparatorWrapper(comparator: java.util.Comparator<in PsiElement?>): Comparator<PsiElement?> =
    Comparator { o1: PsiElement?, o2: PsiElement? ->
        val diff = comparator.compare(o1, o2)
        if (diff == 0) {
            return@Comparator runReadAction { PsiUtilCore.compareElementsByPosition(o1, o2) }
        }
        diff
    }

private abstract class OverridingMembersUpdater constructor(
    project: Project?,
    @Nls title: String,
    renderer: PsiElementListCellRenderer<NavigatablePsiElement>
) : BackgroundUpdaterTask(project, title, comparatorWrapper(renderer.comparator as Comparator<in PsiElement?>)) {
    fun collectFunctionalInheritors(indicator: ProgressIndicator, member: PsiMember?) {
        val search =
            if (member is PsiClass) {
                FunctionalExpressionSearch.search(member)
            } else {
                FunctionalExpressionSearch.search((member as PsiMethod?)!!)
            }
        search.forEach(Processor { expr: PsiFunctionalExpression? ->
            if (!updateComponent(expr!!)) {
                indicator.cancel()
            }
            ProgressManager.checkCanceled()
            true
        })
    }
}

private class SubclassUpdater(
    private val psiClass: PsiClass,
    renderer: PsiElementListCellRenderer<NavigatablePsiElement>,
    private val inheritors: MutableList<NavigatablePsiElement>
) : OverridingMembersUpdater(psiClass.project, JavaAnalysisBundle.message("subclasses.search.progress.title"), renderer) {
    @Nls
    override fun getCaption(size: Int): String {
        val suffix = if (isFinished) "" else " so far"
        return if (psiClass.isInterface) CodeInsightBundle.message(
            "goto.implementation.chooserTitle",
            psiClass.name,
            size,
            suffix
        ) else DaemonBundle.message("navigation.title.subclass", psiClass.name, size, suffix)
    }

    override fun onSuccess() {
        super.onSuccess()
        theOnlyOneElement.safeAs<NavigatablePsiElement>()?.let {
            it.navigate(true)
            myPopup.cancel()
        }
    }

    override fun run(indicator: ProgressIndicator) {
        super.run(indicator)
        inheritors.forEach(::updateComponent)
        collectFunctionalInheritors(indicator, psiClass)
    }
}
