// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.lineMarkers

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.NavigateAction
import com.intellij.codeInsight.daemon.impl.GutterTooltipBuilder
import com.intellij.codeInsight.daemon.impl.InheritorsLineMarkerNavigator
import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Function
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.dsl.collectHighlightingDslMarkers
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.AbstractKotlinLineMarkerProvider
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared.LineMarkerInfos
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.highlighter.markers.InheritanceMergeableLineMarkerInfo
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinGutterTooltipHelper
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinLineMarkerOptions
import org.jetbrains.kotlin.idea.k2.codeinsight.KotlinGoToSuperDeclarationsHandler
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isInheritable
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.hasBody
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference

class KotlinLineMarkerProvider : AbstractKotlinLineMarkerProvider() {

    override fun doCollectSlowLineMarkers(elements: List<PsiElement>, result: LineMarkerInfos) {
        for (element in elements) {
            if (!(element is LeafPsiElement && element.elementType == KtTokens.IDENTIFIER)) continue
            val declaration = element.parent as? KtNamedDeclaration ?: continue
            when (declaration) {
                is KtClass -> {
                    collectInheritedClassMarker(declaration, result)
                    collectHighlightingDslMarkers(declaration, result)
                }

                is KtCallableDeclaration -> {
                    collectSuperDeclarations(declaration, result)
                    collectCallableOverridings(declaration, result)
                }
            }
        }
    }

    private fun collectCallableOverridings(element: KtCallableDeclaration, result: MutableCollection<in LineMarkerInfo<*>>) {
        if (!element.isOverridable()) {
            return
        }

        val klass = element.containingClassOrObject ?: return
        if (klass !is KtClass) return

        val isAbstract = CallableOverridingsTooltip.isAbstract(element, klass)
        val gutter = if (isAbstract) KotlinLineMarkerOptions.implementedOption else KotlinLineMarkerOptions.overriddenOption
        if (!gutter.isEnabled) return
        if (element.findAllOverridings().firstOrNull() == null &&
            (element.hasBody() || !isUsedSamInterface(klass))) return

        val anchor = element.nameIdentifier ?: element

        val icon = gutter.icon ?: return

        val lineMarkerInfo = InheritanceMergeableLineMarkerInfo(
            anchor,
            anchor.textRange,
            icon,
            CallableOverridingsTooltip,
            ImplementationsPopupHandler,
            GutterIconRenderer.Alignment.RIGHT
        ) { gutter.name }

        NavigateAction.setNavigateAction(
            lineMarkerInfo,
            KotlinBundle.message("highlighter.action.text.go.to.overridden.methods"),
            IdeActions.ACTION_GOTO_IMPLEMENTATION
        )
        result.add(lineMarkerInfo)
    }

    private fun collectSuperDeclarations(declaration: KtCallableDeclaration, result: MutableCollection<in LineMarkerInfo<*>>) {
        if (!(declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) || (declaration.containingFile as KtFile).isCompiled)) {
            return
        }

        analyze(declaration) {
            var callableSymbol = declaration.symbol as? KaCallableSymbol ?: return
            if (callableSymbol is KaValueParameterSymbol) {
                callableSymbol = callableSymbol.generatedPrimaryConstructorProperty ?: return
            }
            val allOverriddenSymbols = callableSymbol.allOverriddenSymbols.toList()
            if (allOverriddenSymbols.isEmpty()) return
            val implements = callableSymbol.modality != KaSymbolModality.ABSTRACT &&
                    allOverriddenSymbols.all { it.modality == KaSymbolModality.ABSTRACT }
            val gutter = if (implements) KotlinLineMarkerOptions.implementingOption else KotlinLineMarkerOptions.overridingOption
            if (!gutter.isEnabled) return
            val anchor = declaration.nameIdentifier ?: declaration
            val lineMarkerInfo = InheritanceMergeableLineMarkerInfo(
                anchor,
                anchor.textRange,
                gutter.icon!!,
                SuperDeclarationMarkerTooltip,
                SuperDeclarationPopupHandler,
                GutterIconRenderer.Alignment.RIGHT,
            ) { gutter.name }

            NavigateAction.setNavigateAction(
                lineMarkerInfo,
                if (declaration is KtNamedFunction)
                    KotlinBundle.message("highlighter.action.text.go.to.super.method")
                else
                    KotlinBundle.message("highlighter.action.text.go.to.super.property"),
                IdeActions.ACTION_GOTO_SUPER
            )
            result.add(lineMarkerInfo)
        }
    }

    private fun collectInheritedClassMarker(element: KtClass, result: MutableCollection<in LineMarkerInfo<*>>) {
        if (!element.isInheritable()) {
            return
        }

        val anchor = element.nameIdentifier ?: element
        val isInterface = element.isInterface()
        val gutter = if (isInterface) KotlinLineMarkerOptions.implementedOption else KotlinLineMarkerOptions.overriddenOption
        if (!gutter.isEnabled) return

        if (!KotlinFindUsagesSupport.searchInheritors(element, element.useScope, searchDeeply = false).iterator().hasNext() &&
            !isUsedSamInterface(element)) return

        val icon = gutter.icon ?: return

        val lineMarkerInfo = InheritanceMergeableLineMarkerInfo(
            anchor,
            anchor.textRange,
            icon,
            ClassInheritorsTooltip,
            ImplementationsPopupHandler,
            GutterIconRenderer.Alignment.RIGHT
        ) { gutter.name }

        NavigateAction.setNavigateAction(
            lineMarkerInfo,
            if (isInterface)
                KotlinBundle.message("highlighter.action.text.go.to.implementations")
            else
                KotlinBundle.message("highlighter.action.text.go.to.subclasses"),
            IdeActions.ACTION_GOTO_IMPLEMENTATION
        )
        result.add(lineMarkerInfo)
    }

    private fun isUsedSamInterface(element: KtClass): Boolean = element.toLightClass()
        ?.let { aClass -> LambdaUtil.isFunctionalClass(aClass) && ReferencesSearch.search(aClass).findFirst() != null } == true

}

object SuperDeclarationPopupHandler : GutterIconNavigationHandler<PsiElement> {
    override fun navigate(e: MouseEvent, element: PsiElement?) {
        val declaration = element?.getParentOfType<KtDeclaration>(false) ?: return
        KotlinGoToSuperDeclarationsHandler.gotoSuperDeclarations(declaration)?.show(RelativePoint(e))
    }
}

object ImplementationsPopupHandler : InheritorsLineMarkerNavigator() {
    override fun getMessageForDumbMode(): String = KotlinBundle.message("notification.navigation.to.overriding.classes")
}

private fun comparator(): Comparator<PsiElement> = Comparator.comparing { el ->
    val presentation = GotoTargetHandler.computePresentation(el, false)
    val containerText = presentation.containerText
    presentation.presentableText + (if (containerText != null) " $containerText" else "")
}

object ClassInheritorsTooltip : Function<PsiElement, String> {
    override fun `fun`(element: PsiElement): String? {
        val ktClass = element.parent as? KtClass ?: return null
        var inheritors = KotlinFindUsagesSupport.searchInheritors(ktClass, ktClass.useScope).take(5).toList()
        if (inheritors.isEmpty()) {
            inheritors = findFunctionalExpressions(ktClass)
            if (inheritors.isEmpty()) {
                return null
            }
        }
        val isInterface = ktClass.isInterface()
        if (inheritors.size == 5) {
            return if (isInterface) DaemonBundle.message("method.is.implemented.too.many") else DaemonBundle.message("class.is.subclassed.too.many")
        }
        val start =
            KotlinBundle.message(if (isInterface) "tooltip.is.implemented.by" else "tooltip.is.subclassed.by")


        return KotlinGutterTooltipHelper.buildTooltipText(
            inheritors.sortedWith(comparator()),
            start, true, IdeActions.ACTION_GOTO_IMPLEMENTATION)
    }
}

private fun findFunctionalExpressions(ktClass: KtClass): List<PsiElement> {
    val lightClass = ktClass.toLightClass()
    if (lightClass != null && LambdaUtil.isFunctionalClass(lightClass)) {
        return FunctionalExpressionSearch.search(lightClass, ktClass.useScope).asIterable().asSequence().take(5).toList()
    }
    return emptyList()
}

object CallableOverridingsTooltip : Function<PsiElement, String> {
    override fun `fun`(element: PsiElement): String? {
        val declaration = element.getParentOfType<KtCallableDeclaration>(false) ?: return null
        val klass = declaration.containingClassOrObject as? KtClass ?: return null
        var overridings = KotlinFindUsagesSupport.searchOverriders(declaration, declaration.useScope).take(5).toList()
        if (overridings.isEmpty()) {
            overridings = findFunctionalExpressions(klass)
            if (overridings.isEmpty()) {
                return null
            }
        }
        val isAbstract = isAbstract(declaration, klass)
        if (overridings.size == 5) {
            return if (isAbstract) DaemonBundle.message("method.is.implemented.too.many") else DaemonBundle.message("method.is.overridden.too.many")
        }
        else {
            val start = if (isAbstract)
                KotlinBundle.message("overridden.marker.implementation")
            else
                KotlinBundle.message("overridden.marker.overrides")

            return KotlinGutterTooltipHelper.buildTooltipText(
                overridings.sortedWith(comparator()),
                start, true, IdeActions.ACTION_GOTO_IMPLEMENTATION)
        }
    }

    fun isAbstract(declaration: KtCallableDeclaration, klass: KtClass) =
        declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
                klass.isInterface() && if (declaration is KtDeclarationWithBody) !declaration.hasBody() else true
}

object SuperDeclarationMarkerTooltip : Function<PsiElement, String> {
    override fun `fun`(element: PsiElement): String? {
        val declaration = element.getParentOfType<KtCallableDeclaration>(false) ?: return null
        if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return null
        analyze(declaration) {
            var callableSymbol = declaration.symbol as? KaCallableSymbol ?: return null
            if (callableSymbol is KaValueParameterSymbol) {
                callableSymbol = callableSymbol.generatedPrimaryConstructorProperty ?: return null
            }
            val allOverriddenSymbols = callableSymbol.directlyOverriddenSymbols.toList()
            if (allOverriddenSymbols.isEmpty()) return ""
            val isAbstract = callableSymbol.modality == KaSymbolModality.ABSTRACT
            val abstracts = hashSetOf<PsiElement>()
            val supers = allOverriddenSymbols.mapNotNull {
                val superFunction = it.psi
                if (superFunction != null && it.modality == KaSymbolModality.ABSTRACT) {
                    abstracts.add(superFunction)
                }
                superFunction
            }
            val divider = GutterTooltipBuilder.getElementDivider(false, false, supers.size)
            val reference = AtomicReference("")

            return KotlinGutterTooltipHelper.buildTooltipText(
                supers.sortedWith(comparator()),
                { superMethod: PsiElement? ->
                    val isProperty = superMethod is KtProperty || superMethod is KtParameter
                    val key =
                        if (abstracts.contains(superMethod) && !isAbstract) {
                            if (isProperty) "tooltip.implements.property" else "tooltip.implements.function"
                        } else {
                            if (isProperty) "tooltip.overrides.property" else "tooltip.overrides.function"
                        }
                    reference.getAndSet(divider) + KotlinBundle.message(key) + " "
                },
                { true },
                IdeActions.ACTION_GOTO_SUPER
            )
        }
    }
}