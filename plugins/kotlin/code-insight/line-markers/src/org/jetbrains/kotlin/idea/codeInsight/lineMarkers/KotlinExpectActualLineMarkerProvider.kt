// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.lineMarkers

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.NavigateAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.psi.isEffectivelyActual
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.*
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinLineMarkerOptions
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import java.awt.event.MouseEvent

class KotlinExpectActualLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName() = KotlinBundle.message("highlighter.name.expect.actual.line.markers")

    override fun getOptions(): Array<Option> = arrayOf(KotlinLineMarkerOptions.expectOption, KotlinLineMarkerOptions.actualOption)

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null
    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        if (elements.isEmpty()) return
        val expectEnabled = KotlinLineMarkerOptions.expectOption.isEnabled
        val actualEnabled = KotlinLineMarkerOptions.actualOption.isEnabled
        if (!expectEnabled && !actualEnabled) return

        val first = elements.first()
        if (DumbService.getInstance(first.project).isDumb) return

        for (element in elements) {
            val declaration = element as? KtNamedDeclaration ?: continue
            if (expectEnabled) {
                if (!declaration.isExpectDeclaration() && declaration.isEffectivelyActual()) {
                    collectExpectMarkers(declaration, result)
                }
            }
        }

        for (element in elements) {
            val declaration = element as? KtNamedDeclaration ?: continue

            if (actualEnabled) {
                if (declaration.isExpectDeclaration()) {
                    collectActualMarkers(declaration, result)
                    continue
                }
            }
        }
    }


    private fun collectActualMarkers(
        declaration: KtNamedDeclaration,
        result: MutableCollection<in LineMarkerInfo<*>>
    ): Boolean {
        val gutter = KotlinLineMarkerOptions.actualOption
        if (!gutter.isEnabled) return false

        if (declaration.areMarkersForbidden()) return false
        if (!declaration.hasExpectModifier() && declaration.containingClassOrObject?.hasExpectModifier() != true) return false
        val anchor = declaration.expectOrActualAnchor

        val navigatableActualDeclarations: Collection<SmartPsiElementPointer<KtDeclaration>>? = anchor.markerDeclaration?.allNavigatableActualDeclarations()
        if (navigatableActualDeclarations.isNullOrEmpty()) return false

        val navigationHandler: GutterIconNavigationHandler<PsiElement> =
            GutterIconNavigationHandler<PsiElement> { e, element ->
                e?.let { buildNavigateToActualDeclarationsPopup(element, navigatableActualDeclarations)?.showPopup(e) }
            }

        val lineMarkerInfo = LineMarkerInfo(
            /* element = */ anchor,
            /* range = */ anchor.textRange,
            /* icon = */ gutter.icon!!,
            /* tooltipProvider = */
            { el -> el.markerDeclaration?.let { actualTooltip(navigatableActualDeclarations) } },
            /* navHandler = */
            object : GutterIconNavigationHandler<PsiElement>, TestableLineMarkerNavigator {
                override fun navigate(e: MouseEvent?, elt: PsiElement?) {
                    navigationHandler.navigate(e, elt)
                }

                override fun getTargetsPopupDescriptor(element: PsiElement?): NavigationPopupDescriptor? {
                    return buildNavigateToActualDeclarationsPopup(element, navigatableActualDeclarations)
                }
            },
            /* alignment = */ GutterIconRenderer.Alignment.RIGHT,
        ) { gutter.name }

        NavigateAction.setNavigateAction(
            lineMarkerInfo,
            KotlinBundle.message("highlighter.action.text.go.to.actual.declarations"),
            IdeActions.ACTION_GOTO_IMPLEMENTATION
        )
        result.add(lineMarkerInfo)

        return true
    }


    private fun collectExpectMarkers(
        declaration: KtNamedDeclaration,
        result: MutableCollection<in LineMarkerInfo<*>>
    ): Boolean {
        val gutter = KotlinLineMarkerOptions.expectOption
        if (!gutter.isEnabled) return false

        if (declaration.areMarkersForbidden()) return false
        if (!declaration.hasActualModifier()) return false
        if (!hasExpectForActual(declaration)) return false

        val anchor = declaration.expectOrActualAnchor

        val navigatableExpectedDeclarations: List<SmartPsiElementPointer<KtDeclaration>>? =
            anchor.markerDeclaration?.allNavigatableExpectedDeclarations()

        val navigationHandler: GutterIconNavigationHandler<PsiElement> =
            GutterIconNavigationHandler<PsiElement> { e, element ->
                if (e != null) {
                    buildNavigateToExpectedDeclarationsPopup(element, navigatableExpectedDeclarations)?.showPopup(e)
                }
            }

        val lineMarkerInfo = LineMarkerInfo(
            /* element = */ anchor,
            /* range = */ anchor.textRange,
            /* icon = */ gutter.icon!!,
            /* tooltipProvider = */
            { el ->
                if (el.markerDeclaration != null) {
                    expectTooltip(navigatableExpectedDeclarations)
                } else {
                    null
                }
            },
            /* navHandler = */
            object : GutterIconNavigationHandler<PsiElement>, TestableLineMarkerNavigator {
                override fun navigate(e: MouseEvent?, elt: PsiElement?) {
                    navigationHandler.navigate(e, elt)
                }

                override fun getTargetsPopupDescriptor(element: PsiElement?): NavigationPopupDescriptor? {
                    return buildNavigateToExpectedDeclarationsPopup(element, navigatableExpectedDeclarations)
                }
            },
            /* alignment = */ GutterIconRenderer.Alignment.RIGHT,
        ) { gutter.name }

        NavigateAction.setNavigateAction(
            lineMarkerInfo,
            KotlinBundle.message("highlighter.action.text.go.to.expected.declaration"),
            null
        )
        result.add(lineMarkerInfo)
        return true
    }
}

internal fun getModulesStringForMarkerTooltip(navigatableDeclarations: Collection<SmartPsiElementPointer<KtDeclaration>>?): String? {
    if (navigatableDeclarations.isNullOrEmpty()) {
        return null
    }

    val project = navigatableDeclarations.first().project
    val moduleNames = navigatableDeclarations
        .mapNotNull { navigatable -> navigatable.element?.getKaModule(project, useSiteModule = null)?.moduleName }

    return when (moduleNames.size) {
        0 -> null
        1 -> moduleNames.single()
        else -> moduleNames.sorted().joinToString(", ", prefix = "[", postfix = "]")
    }
}

@OptIn(KaExperimentalApi::class)
private val KaModule.moduleName: String
    get() = (this as? KaSourceModule)?.name ?: moduleDescription

fun expectTooltip(navigatableDeclarations: Collection<SmartPsiElementPointer<KtDeclaration>>?): String? {
    val modulesString = getModulesStringForMarkerTooltip(navigatableDeclarations) ?: return null
    return KotlinBundle.message("highlighter.tool.tip.has.expect.declaration.in", modulesString, if (navigatableDeclarations?.size == 1) 0 else 1)
}

internal fun buildNavigateToExpectedDeclarationsPopup(
    element: PsiElement?,
    navigatableExpectedDeclarations: List<SmartPsiElementPointer<KtDeclaration>>?
): NavigationPopupDescriptor? =
    buildNavigateToExpectedDeclarationsPopup(element) { navigatableExpectedDeclarations?.mapNotNull { it.element } ?: emptyList() }

// actual

fun actualTooltip(navigatableDeclarations: Collection<SmartPsiElementPointer<KtDeclaration>>?): String? {
    val modulesString = getModulesStringForMarkerTooltip(navigatableDeclarations) ?: return null

    return KotlinBundle.message("highlighter.prefix.text.has.actuals.in", modulesString, if (navigatableDeclarations?.size == 1) 0 else 1)
}

fun buildNavigateToActualDeclarationsPopup(element: PsiElement?, navigatableActualDeclarations: Collection<SmartPsiElementPointer<KtDeclaration>>): NavigationPopupDescriptor? =
    buildNavigateToActualDeclarationsPopup(element) { navigatableActualDeclarations.mapNotNull { it.element } }
