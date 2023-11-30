// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.NavigationPopupDescriptor
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.buildNavigateToActualDeclarationsPopup
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.findMarkerBoundDeclarations
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.util.actualsForExpected
import org.jetbrains.kotlin.psi.KtDeclaration

@ApiStatus.Internal
fun getPlatformActualTooltip(declaration: KtDeclaration): String? {
    val actualDeclarations = declaration.actualsForExpected().mapNotNull { it.toDescriptor() }
    val modulesString = getModulesStringForExpectActualMarkerTooltip(actualDeclarations) ?: return null

    return KotlinBundle.message(
        "highlighter.prefix.text.has.actuals.in",
        modulesString,
        if (actualDeclarations.size == 1) 0 else 1
    )
}

fun KtDeclaration.allNavigatableActualDeclarations(): Set<KtDeclaration> =
    actualsForExpected() + findMarkerBoundDeclarations().flatMap { it.actualsForExpected().asSequence() }


fun buildNavigateToActualDeclarationsPopup(element: PsiElement?): NavigationPopupDescriptor? =
    buildNavigateToActualDeclarationsPopup(element, KtDeclaration::allNavigatableActualDeclarations)
