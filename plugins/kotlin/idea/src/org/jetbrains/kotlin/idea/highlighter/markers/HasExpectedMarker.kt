// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.util.expectedDescriptors
import org.jetbrains.kotlin.idea.util.expectedDeclarationIfAny
import org.jetbrains.kotlin.psi.KtDeclaration

fun getExpectedDeclarationTooltip(declaration: KtDeclaration): String? {
    val descriptor = declaration.toDescriptor() as? MemberDescriptor ?: return null
    val expectDescriptors = descriptor.expectedDescriptors()
    val modulesString = getModulesStringForExpectActualMarkerTooltip(expectDescriptors) ?: return null

    return KotlinBundle.message(
        "highlighter.tool.tip.has.expect.declaration.in", modulesString
    )
}

fun KtDeclaration.allNavigatableExpectedDeclarations(): List<KtDeclaration> =
    listOfNotNull(expectedDeclarationIfAny()) + findMarkerBoundDeclarations().mapNotNull { it.expectedDeclarationIfAny() }

@NlsContexts.PopupTitle
fun KtDeclaration.navigateToExpectedTitle() = KotlinBundle.message("highlighter.title.choose.expected.for", name.toString())

@NlsContexts.TabTitle
fun KtDeclaration.navigateToExpectedUsagesTitle() = KotlinBundle.message("highlighter.title.expected.for", name.toString())

fun buildNavigateToExpectedDeclarationsPopup(element: PsiElement?): NavigationPopupDescriptor? {
    return element?.markerDeclaration?.let {
        val navigatableExpectedDeclarations = it.allNavigatableExpectedDeclarations()
        if (navigatableExpectedDeclarations.isEmpty()) return null
        return NavigationPopupDescriptor(
            navigatableExpectedDeclarations,
            it.navigateToExpectedTitle(),
            it.navigateToExpectedUsagesTitle(),
            ActualExpectedPsiElementCellRenderer()
        )
    }
}
