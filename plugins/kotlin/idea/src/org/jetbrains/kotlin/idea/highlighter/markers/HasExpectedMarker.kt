// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.NavigationPopupDescriptor
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.buildNavigateToExpectedDeclarationsPopup
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.findMarkerBoundDeclarations
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.util.expectedDescriptors
import org.jetbrains.kotlin.idea.util.expectedDeclarationIfAny
import org.jetbrains.kotlin.psi.KtDeclaration

fun getExpectedDeclarationTooltip(declaration: KtDeclaration): String? {
    val descriptor = declaration.toDescriptor() as? MemberDescriptor ?: return null
    val expectDescriptors = descriptor.expectedDescriptors()
    val modulesString = getModulesStringForExpectActualMarkerTooltip(expectDescriptors) ?: return null

    return KotlinBundle.message(
        "highlighter.tool.tip.has.expect.declaration.in",
        modulesString,
        if (expectDescriptors.size == 1) 0 else 1
    )
}

fun KtDeclaration.allNavigatableExpectedDeclarations(): List<KtDeclaration> =
    listOfNotNull(expectedDeclarationIfAny()) + findMarkerBoundDeclarations().mapNotNull { it.expectedDeclarationIfAny() }

fun buildNavigateToExpectedDeclarationsPopup(element: PsiElement?): NavigationPopupDescriptor? =
    buildNavigateToExpectedDeclarationsPopup(element, KtDeclaration::allNavigatableExpectedDeclarations)
