// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinLineMarkerOptions
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments

typealias LineMarkerInfos = MutableCollection<in LineMarkerInfo<*>>

abstract class AbstractKotlinLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = KotlinLineMarkersSharedBundle.message("highlighter.name.kotlin.line.markers")

    override fun getOptions(): Array<Option> = KotlinLineMarkerOptions.options

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? {
        if (DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS) {
            if (element.canHaveSeparator()) {
                val prevSibling = element.getPrevSiblingIgnoringWhitespaceAndComments()
                if (prevSibling.canHaveSeparator() &&
                    (element.wantsSeparator() || prevSibling?.wantsSeparator() == true)
                ) {
                    return createLineSeparatorByElement(element)
                }
            }
        }

        return null
    }


    private fun PsiElement?.canHaveSeparator(): Boolean =
        this is KtFunction
                || this is KtClassInitializer
                || (this is KtProperty && !isLocal)
                || ((this is KtObjectDeclaration && this.isCompanion()))

    private fun PsiElement.wantsSeparator(): Boolean = this is KtFunction || StringUtil.getLineBreakCount(text) > 0

    private fun createLineSeparatorByElement(element: PsiElement): LineMarkerInfo<PsiElement> {
        val anchor = PsiTreeUtil.getDeepestFirst(element)

        val info = LineMarkerInfo(anchor, anchor.textRange)
        info.separatorColor = EditorColorsManager.getInstance().globalScheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
        info.separatorPlacement = SeparatorPlacement.TOP
        return info
    }

    final override fun collectSlowLineMarkers(elements: List<PsiElement>, result: LineMarkerInfos) {
        if (elements.isEmpty()) return
        if (KotlinLineMarkerOptions.options.none { option -> option.isEnabled }) return

        val first = elements.first()
        if (DumbService.getInstance(first.project).isDumb || !RootKindFilter.projectAndLibrarySources.matches(first)) return

        doCollectSlowLineMarkers(elements, result)
    }

    abstract fun doCollectSlowLineMarkers(elements: List<PsiElement>, result: LineMarkerInfos)

}