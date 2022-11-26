// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import javax.swing.Icon

class OverriddenMergeableLineMarkerInfo(
    element: PsiElement,
    textRange: TextRange,
    icon: Icon,
    tooltip: Function<in PsiElement, String>,
    navigationHandler: GutterIconNavigationHandler<PsiElement>,
    alignment: GutterIconRenderer.Alignment,
    accessibleNameProvider: () -> String
) : MergeableLineMarkerInfo<PsiElement>(element, textRange, icon, tooltip, navigationHandler, alignment, accessibleNameProvider) {

    override fun canMergeWith(info: MergeableLineMarkerInfo<*>): Boolean = info is OverriddenMergeableLineMarkerInfo && info.icon == icon

    override fun getCommonIcon(infos: List<MergeableLineMarkerInfo<*>>): Icon = infos.first().icon
}
