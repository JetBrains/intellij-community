// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.ide.DataManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.highlighting.dsl.DslStyleUtils
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.highlighter.dsl.DslKotlinHighlightingVisitorExtension
import org.jetbrains.kotlin.idea.highlighter.dsl.isDslHighlightingMarker
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtScriptInitializer
import javax.swing.JComponent

private val navHandler = GutterIconNavigationHandler<PsiElement> { event, element ->
    val dataContext = (event.component as? JComponent)?.let { DataManager.getInstance().getDataContext(it) }
        ?: return@GutterIconNavigationHandler
    val ktClass = element?.parent as? KtClass ?: return@GutterIconNavigationHandler
    val styleId = ktClass.styleIdForMarkerAnnotation() ?: return@GutterIconNavigationHandler
    ColorAndFontOptions.selectOrEditColor(dataContext, DslStyleUtils.styleOptionDisplayName(styleId), KotlinLanguage.NAME)
}

private val toolTipHandler = Function<PsiElement, String> {
    KotlinBundle.message("highlighter.tool.tip.marker.annotation.for.dsl")
}

fun collectHighlightingColorsMarkers(
    ktClass: KtClass,
    result: LineMarkerInfos
) {
    if (!KotlinLineMarkerOptions.dslOption.isEnabled) return

    val styleId = ktClass.styleIdForMarkerAnnotation() ?: return

    val anchor = ktClass.nameIdentifier ?: return

    @Suppress("MoveLambdaOutsideParentheses")
    result.add(
        LineMarkerInfo(
          anchor,
          anchor.textRange,
          DslStyleUtils.createDslStyleIcon(styleId),
          toolTipHandler,
          navHandler,
          GutterIconRenderer.Alignment.RIGHT,
          { KotlinBundle.message("highlighter.tool.tip.marker.annotation.for.dsl") }
        )
    )
}

private fun KtClass.styleIdForMarkerAnnotation(): Int? {
    val classDescriptor = toDescriptor() as? ClassDescriptor ?: return null
    if (classDescriptor.kind != ClassKind.ANNOTATION_CLASS) return null
    if (!classDescriptor.isDslHighlightingMarker()) return null
    return DslKotlinHighlightingVisitorExtension.styleIdByMarkerAnnotation(classDescriptor)
}

// TODO: copy-paste
fun KtDeclaration.toDescriptor(): DeclarationDescriptor? {
    if (this is KtScriptInitializer) {
        return null
    }

    return resolveToDescriptorIfAny()
}
