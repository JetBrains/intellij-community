// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared

import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.codeInsight.navigation.TargetPresentationProvider
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import java.awt.event.MouseEvent

data class NavigationPopupDescriptor(
    val targets: Collection<PsiElement>,
    @NlsContexts.PopupTitle val title: String,
    @NlsContexts.TabTitle val findUsagesTitle: String,
    val renderer: TargetPresentationProvider<PsiElement>
) {
    fun showPopup(e: MouseEvent) {
        val navigatablePsiElement = targets.firstOrNull() ?: return
        PsiTargetNavigator { targets }
            .presentationProvider(renderer)
            .tabTitle(findUsagesTitle)
            .navigate(e, title, navigatablePsiElement.project)
    }
}

interface TestableLineMarkerNavigator {
    fun getTargetsPopupDescriptor(element: PsiElement?): NavigationPopupDescriptor?
}
