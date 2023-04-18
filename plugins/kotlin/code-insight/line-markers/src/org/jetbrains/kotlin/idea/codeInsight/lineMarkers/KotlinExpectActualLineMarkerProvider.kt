// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.lineMarkers

import com.intellij.codeInsight.daemon.*
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.NavigationPopupDescriptor
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.TestableLineMarkerNavigator
import org.jetbrains.kotlin.idea.highlighter.markers.*
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
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
                if (!declaration.isExpectDeclaration() && declaration.isEffectivelyActualDeclaration()) {
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
        if (!declaration.hasExpectModifier() && declaration.containingClassOrObjectOrSelf()?.hasExpectModifier() != true) return false
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

fun KtDeclaration.isEffectivelyActualDeclaration(checkConstructor: Boolean = true): Boolean = when {
    hasActualModifier() -> true
    this is KtEnumEntry || checkConstructor && this is KtConstructor<*> -> containingClass()?.hasActualModifier() == true
    else -> false
}

fun KtDeclaration.isExpectDeclaration(): Boolean {
    return when {
        hasExpectModifier() -> true
        else -> containingClassOrObject?.isExpectDeclaration() == true
    }
}


fun hasExpectForActual(declaration: KtDeclaration): Boolean {
    return analyze(declaration) {
        val symbol: KtDeclarationSymbol = declaration.getSymbol()
        symbol.getExpectForActual() != null
    }
}

internal fun getModulesStringForMarkerTooltip(navigatableDeclarations: Collection<SmartPsiElementPointer<KtDeclaration>>?): String? {
    if (navigatableDeclarations.isNullOrEmpty()) {
        return null
    }

    val project = navigatableDeclarations.first().project
    val projectStructureProvider = ProjectStructureProvider.getInstance(project)

    return navigatableDeclarations
        .mapNotNull { it.element }
        .joinToString { projectStructureProvider.getModule(it, null).moduleName }
}

private val KtModule.moduleName: String
    get() = (this as? KtSourceModule)?.moduleName ?: moduleDescription

fun expectTooltip(navigatableDeclarations: Collection<SmartPsiElementPointer<KtDeclaration>>?): String? {
    val modulesString = getModulesStringForMarkerTooltip(navigatableDeclarations) ?: return null
    return KotlinBundle.message("highlighter.tool.tip.has.expect.declaration.in", modulesString, if (navigatableDeclarations?.size == 1) 0 else 1)
}

internal fun KtDeclaration.allNavigatableExpectedDeclarations(): List<SmartPsiElementPointer<KtDeclaration>> {
    return listOfNotNull(expectedDeclarationIfAny()) + findMarkerBoundDeclarations().mapNotNull { it.expectedDeclarationIfAny() }
}

internal fun buildNavigateToExpectedDeclarationsPopup(
    element: PsiElement?,
    navigatableExpectedDeclarations: List<SmartPsiElementPointer<KtDeclaration>>?
): NavigationPopupDescriptor? =
    buildNavigateToExpectedDeclarationsPopup(element) { navigatableExpectedDeclarations?.mapNotNull { it.element } ?: emptyList() }

internal fun KtDeclaration.expectedDeclarationIfAny(): SmartPsiElementPointer<KtDeclaration>? {
    val declaration = this
    return analyze(this) {
        val symbol: KtDeclarationSymbol = declaration.getSymbol()
        (symbol.getExpectForActual()?.psi as? KtDeclaration)?.createSmartPointer()
    }
}


// actual

fun actualTooltip(navigatableDeclarations: Collection<SmartPsiElementPointer<KtDeclaration>>?): String? {
    val modulesString = getModulesStringForMarkerTooltip(navigatableDeclarations) ?: return null

    return KotlinBundle.message("highlighter.prefix.text.has.actuals.in", modulesString, if (navigatableDeclarations?.size == 1) 0 else 1)
}

@RequiresBackgroundThread(generateAssertion = false)
internal fun KtDeclaration.findAllExpectForActual(searchScope: SearchScope = runReadAction { useScope }): Sequence<SmartPsiElementPointer<KtDeclaration>> {
    val declaration = this
    val scope = searchScope as? GlobalSearchScope ?: return emptySequence()
    val containingClassOrObjectOrSelf = containingClassOrObjectOrSelf()
    // covers cases like classes, class functions and class properties
    containingClassOrObjectOrSelf?.fqName?.let { fqName ->
        val classOrObjects = KotlinFullClassNameIndex.getAllElements(fqName.asString(), project, scope, filter = {
            it.matchesWithActual(containingClassOrObjectOrSelf)
        })
        return if (classOrObjects.isNotEmpty()) {
            classOrObjects.asSequence().mapNotNull { classOrObject ->
                when (declaration) {
                    is KtClassOrObject -> classOrObject
                    is KtNamedDeclaration -> classOrObject.declarations.firstOrNull {
                        it is KtNamedDeclaration && it.name == declaration.name && it.matchesWithActual(declaration)
                    }

                    else -> null
                }?.createSmartPointer()
            }
        } else {
            val typeAliases = KotlinTopLevelTypeAliasFqNameIndex.getAllElements(fqName.asString(), project, scope, filter = {
                it.matchesWithActual(containingClassOrObjectOrSelf)
            })
            typeAliases.asSequence().mapNotNull { classOrObject ->
                when (declaration) {
                    is KtClassOrObject -> classOrObject
                    else -> null
                }?.createSmartPointer()
            }
        }
    }
    // top level functions
    val packageFqName = declaration.containingKtFile.packageFqName
    val topLevelFqName = packageFqName.child(Name.identifier(declaration.name!!)).asString()
    return when (declaration) {
        is KtNamedFunction -> {
            KotlinTopLevelFunctionFqnNameIndex.getAllElements(topLevelFqName, project, scope) {
                it.matchesWithActual(declaration)
            }.asSequence().map(KtNamedFunction::createSmartPointer)
        }
        is KtProperty -> {
            KotlinTopLevelPropertyFqnNameIndex.getAllElements(topLevelFqName, project, scope) {
                it.matchesWithActual(declaration)
            }.asSequence().map(KtProperty::createSmartPointer)
        }
        else -> emptySequence()
    }
}

private fun KtDeclaration.matchesWithActual(actualDeclaration: KtDeclaration): Boolean {
    val declaration = this
    return declaration.hasActualModifier() && analyze(declaration) {
        val symbol: KtDeclarationSymbol = declaration.getSymbol()
        val psi = symbol.getExpectForActual()?.psi as? KtDeclaration
        psi == actualDeclaration
    }
}

fun KtDeclaration.allNavigatableActualDeclarations(): Collection<SmartPsiElementPointer<KtDeclaration>> =
    findAllExpectForActual().toSet() + findMarkerBoundDeclarations().flatMap { it.findAllExpectForActual() }


fun buildNavigateToActualDeclarationsPopup(element: PsiElement?, navigatableActualDeclarations: Collection<SmartPsiElementPointer<KtDeclaration>>): NavigationPopupDescriptor? =
    buildNavigateToActualDeclarationsPopup(element) { navigatableActualDeclarations.mapNotNull { it.element } }

private fun KtElement.containingClassOrObjectOrSelf(): KtClassOrObject? = parentOfType(withSelf = true)