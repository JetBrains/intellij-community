// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.util.actualsForExpected
import org.jetbrains.kotlin.psi.KtDeclaration

@Suppress("DuplicatedCode")
fun getPlatformActualTooltip(declaration: KtDeclaration): String? {
    val actualDeclarations = declaration.actualsForExpected().mapNotNull { it.toDescriptor() }
    val modulesString = getModulesStringForExpectActualMarkerTooltip(actualDeclarations) ?: return null

    return KotlinBundle.message("highlighter.prefix.text.has.actuals.in", modulesString)
}

fun KtDeclaration.allNavigatableActualDeclarations(): Set<KtDeclaration> =
    actualsForExpected() + findMarkerBoundDeclarations().flatMap { it.actualsForExpected().asSequence() }

class ActualExpectedPsiElementCellRenderer : DefaultPsiElementCellRenderer() {
    override fun getContainerText(element: PsiElement?, name: String?) = ""
}

@Nls
fun KtDeclaration.navigateToActualTitle() = KotlinBundle.message("highlighter.title.choose.actual.for", name.toString())

@Nls
fun KtDeclaration.navigateToActualUsagesTitle() = KotlinBundle.message("highlighter.title.actuals.for", name.toString())

fun buildNavigateToActualDeclarationsPopup(element: PsiElement?): NavigationPopupDescriptor? {
    return element?.markerDeclaration?.let {
        val navigatableActualDeclarations = it.allNavigatableActualDeclarations()
        if (navigatableActualDeclarations.isEmpty()) return null
        return NavigationPopupDescriptor(
            navigatableActualDeclarations,
            it.navigateToActualTitle(),
            it.navigateToActualUsagesTitle(),
            ActualExpectedPsiElementCellRenderer()
        )
    }
}
