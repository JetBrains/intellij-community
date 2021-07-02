// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo
import org.jetbrains.kotlin.idea.core.isAndroidModule
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.util.actualsForExpected
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.descriptorUtil.module

private fun ModuleDescriptor?.getPlatformName(): String? {
    if (this == null) return null
    val moduleInfo = getCapability(ModuleInfo.Capability) as? ModuleSourceInfo
    if (moduleInfo != null && moduleInfo.module.isAndroidModule()) {
        return "Android"
    }
    val platform = platform ?: return null

    // TODO(dsavvinov): use better description
    return when {
        platform.isCommon() -> "common"
        else -> {
            assert(platform.componentPlatforms.map { it.platformName }.toSet().size == 1) {
                "Expected the same platform name for component platforms in non-common module"
            }
            platform.first().platformName
        }
    }
}

fun getPlatformActualTooltip(declaration: KtDeclaration): String? {
    val actualDeclarations = declaration.actualsForExpected()
    if (actualDeclarations.isEmpty()) return null

    return actualDeclarations.asSequence()
        .mapNotNull { it.toDescriptor()?.module }
        .groupBy { it.getPlatformName() }
        .filter { (platform, _) -> platform != null }
        .entries
        .joinToString(prefix = KotlinBundle.message("highlighter.prefix.text.has.actuals.in") + " ") { (platform, modules) ->
            val modulesSuffix = if (modules.size <= 1) "" else KotlinBundle.message("highlighter.text.modules", modules.size)
            if (platform == null) {
                throw AssertionError("Platform should not be null")
            }
            platform + modulesSuffix
        }
}

fun KtDeclaration.allNavigatableActualDeclarations(): Set<KtDeclaration> =
    actualsForExpected() + findMarkerBoundDeclarations().flatMap { it.actualsForExpected().asSequence() }

class ActualExpectedPsiElementCellRenderer : DefaultPsiElementCellRenderer() {
    override fun getContainerText(element: PsiElement?, name: String?) = ""
}

fun KtDeclaration.navigateToActualTitle() = KotlinBundle.message("highlighter.title.choose.actual.for", name.toString())

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