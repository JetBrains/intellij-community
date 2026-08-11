// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinLineMarkerOptions

typealias LineMarkerInfos = MutableCollection<in LineMarkerInfo<*>>

abstract class AbstractKotlinLineMarkerProvider(
    private val ignoreAllInjectedElements: Boolean = true,
    private val ignoreLenientInjectedElements: Boolean = true,
) : LineMarkerProviderDescriptor() {

    override fun getOptions(): Array<Option> = KotlinLineMarkerOptions.options

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    final override fun collectSlowLineMarkers(elements: List<PsiElement>, result: LineMarkerInfos) {
        if (elements.isEmpty()) return
        if (options.none { option -> option.isEnabled }) return

        val first = elements.first()
        val project = first.project
        if (
            DumbService.getInstance(project).isDumb ||
            !RootKindFilter.projectAndLibrarySources.matches(first)
        ) return

        val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
        if (injectedLanguageManager.isInjectedFragment(first.containingFile) &&
            (ignoreAllInjectedElements ||
                    ignoreLenientInjectedElements && injectedLanguageManager.shouldInspectionsBeLenient(first))
        ) return

        doCollectSlowLineMarkers(elements, result)
    }

    abstract fun doCollectSlowLineMarkers(elements: List<PsiElement>, result: LineMarkerInfos)

}