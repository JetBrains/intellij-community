// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared

import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.codeInsight.navigation.BackgroundUpdaterTask
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import java.awt.event.MouseEvent
import javax.swing.ListCellRenderer

data class NavigationPopupDescriptor(
    val targets: Collection<NavigatablePsiElement>,
    @NlsContexts.PopupTitle val title: String,
    @NlsContexts.TabTitle val findUsagesTitle: String,
    val renderer: ListCellRenderer<in NavigatablePsiElement>,
    val updater: BackgroundUpdaterTask? = null
) {
    fun showPopup(e: MouseEvent) {
        PsiElementListNavigator.openTargets(e, targets.toTypedArray(), title, findUsagesTitle, renderer, updater)
    }
}

interface TestableLineMarkerNavigator {
    fun getTargetsPopupDescriptor(element: PsiElement?): NavigationPopupDescriptor?
}
