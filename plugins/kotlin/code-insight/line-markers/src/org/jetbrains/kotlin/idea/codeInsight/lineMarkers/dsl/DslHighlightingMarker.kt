// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers.dsl

import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.ide.DataManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.highlighting.dsl.DslStyleUtils
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinLineMarkerOptions
import org.jetbrains.kotlin.idea.highlighting.analyzers.getDslStyleId
import org.jetbrains.kotlin.psi.KtClass
import javax.swing.JComponent

internal fun collectHighlightingDslMarkers(ktClass: KtClass, result: MutableCollection<in LineMarkerInfo<*>>) {
    if (!KotlinLineMarkerOptions.dslOption.isEnabled) return

    val anchor = ktClass.nameIdentifier ?: return
    val styleId = ktClass.getDslStyleId() ?: return

    result.add(
        LineMarkerInfo(
            anchor,
            anchor.textRange,
            DslStyleUtils.createDslStyleIcon(styleId),
            toolTipHandler,
            navHandler(styleId),
            GutterIconRenderer.Alignment.RIGHT,
            KotlinBundle.messagePointer("highlighter.tool.tip.marker.annotation.for.dsl")
        )
    )
}

private fun navHandler(dslStyleId: Int) = GutterIconNavigationHandler<PsiElement> { event, _ ->
    val dataContext = (event.component as? JComponent)?.let { DataManager.getInstance().getDataContext(it) }
        ?: return@GutterIconNavigationHandler
    ColorAndFontOptions.selectOrEditColor(
        dataContext,
        DslStyleUtils.styleOptionDisplayName(dslStyleId),
        KotlinLanguage.NAME
    )
}

private val toolTipHandler = Function<PsiElement, String> {
    KotlinBundle.message("highlighter.tool.tip.marker.annotation.for.dsl")
}
