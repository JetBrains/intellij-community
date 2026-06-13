// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.lineMarkers

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.NavigateAction
import com.intellij.codeInsight.daemon.impl.GutterTooltipBuilder
import com.intellij.codeInsight.daemon.impl.InheritorsLineMarkerNavigator
import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Function
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.psi.isEffectivelyActual
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.dsl.collectHighlightingDslMarkers
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.highlighter.markers.InheritanceMergeableLineMarkerInfo
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinGutterTooltipHelper
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinLineMarkerOptions
import org.jetbrains.kotlin.idea.k2.codeinsight.KotlinGoToSuperDeclarationsHandler
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpect
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isInheritable
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.idea.searching.inheritors.hasAnyActuals
import org.jetbrains.kotlin.idea.searching.inheritors.hasAnyInheritors
import org.jetbrains.kotlin.idea.searching.inheritors.hasAnyOverridings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.hasBody
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference

class KotlinLineMarkerProvider : AbstractKotlinLineMarkerProvider() {

    override fun getName(): String = KotlinLineMarkersBundle.message("highlighter.name.kotlin.line.markers")

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? {
        if (DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS) {
            if (element.canHaveSeparator()) {
                val prevSibling = element.getPrevSiblingIgnoringWhitespaceAndComments()
                if (prevSibling.canHaveSeparator() &&
                    (element.wantsSeparator() || prevSibling?.wantsSeparator() == true)
                ) {
                    return createLineSeparatorByElement(element)
                }
            }
        }

        return null
    }

    private fun PsiElement?.canHaveSeparator(): Boolean =
        this is KtFunction
                || this is KtClassInitializer
                || (this is KtProperty && !isLocal)
                || ((this is KtObjectDeclaration && this.isCompanion()))

    private fun PsiElement.wantsSeparator(): Boolean = this is KtFunction || StringUtil.getLineBreakCount(text) > 0

    private fun createLineSeparatorByElement(element: PsiElement): LineMarkerInfo<PsiElement> {
        val anchor = PsiTreeUtil.getDeepestFirst(element)

        val info = LineMarkerInfo(anchor, anchor.textRange)
        info.separatorColor = EditorColorsManager.getInstance().globalScheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
        info.separatorPlacement = SeparatorPlacement.TOP
        return info
    }

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
        val isExpectDeclaration = element.isExpectDeclaration()
        if (!element.isOverridable() && !isExpectDeclaration) {
            return
        }

        val klass = element.containingClassOrObject ?: return
        if (klass !is KtClass) return

        val isAbstract = CallableOverridingsTooltip.isAbstract(element, klass)
        val gutter = if (isAbstract) KotlinLineMarkerOptions.implementedOption else KotlinLineMarkerOptions.overriddenOption
        if (!gutter.isEnabled) return
        if (!element.hasAnyActuals() && !element.hasAnyOverridings() && (element.hasBody() || !isUsedSamInterface(klass))) return

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
        if (!(declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) || declaration.isEffectivelyActual() || (declaration.containingFile as KtFile).isCompiled)) {
            return
        }

        @OptIn(KaExperimentalApi::class)
        analyze(declaration) {
            var callableSymbol = declaration.symbol as? KaCallableSymbol ?: return
            if (callableSymbol is KaValueParameterSymbol) {
                callableSymbol = callableSymbol.generatedPrimaryConstructorProperty ?: return
            }
            val allOverriddenSymbols = callableSymbol.allOverriddenSymbols.toList()
            if (allOverriddenSymbols.isEmpty() && callableSymbol.getExpectsForActual().isEmpty()) return
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
        val isExpectDeclaration = element.isExpectDeclaration()
        if (!element.isInheritable() && !isExpectDeclaration) {
            return
        }

        val anchor = element.nameIdentifier ?: element
        val isInterface = element.isInterface()
        val gutter = if (isInterface) KotlinLineMarkerOptions.implementedOption else KotlinLineMarkerOptions.overriddenOption
        if (!gutter.isEnabled) return

        if (!element.hasAnyActuals() && !element.hasAnyInheritors() && !isUsedSamInterface(element)) return

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

private const val TOOLTIPS_LIMIT = 5

object ClassInheritorsTooltip : Function<PsiElement, String> {
    override fun `fun`(element: PsiElement): String? {
        val ktClass = element.parent as? KtClass ?: return null
        var inheritors = KotlinFindUsagesSupport.searchInheritors(ktClass, ktClass.useScope).take(TOOLTIPS_LIMIT).toList()
        if (inheritors.isEmpty()) {
            inheritors = if (ktClass.isExpectDeclaration()) ktClass.actualsForExpect().take(TOOLTIPS_LIMIT).toList() else emptyList()
        }
        if (inheritors.isEmpty()) {
            inheritors = findFunctionalExpressions(ktClass)
        }
        val isInterface = ktClass.isInterface()
        if (inheritors.size == TOOLTIPS_LIMIT) {
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
        return FunctionalExpressionSearch.search(lightClass, ktClass.useScope).asIterable().asSequence().take(TOOLTIPS_LIMIT).toList()
    }
    return emptyList()
}

object CallableOverridingsTooltip : Function<PsiElement, String> {
    override fun `fun`(element: PsiElement): String? {
        val declaration = element.getParentOfType<KtCallableDeclaration>(false) ?: return null
        val klass = declaration.containingClassOrObject as? KtClass ?: return null
        var overridings = KotlinFindUsagesSupport.searchOverriders(declaration, declaration.useScope).take(TOOLTIPS_LIMIT).toList()
        if (overridings.isEmpty()) {
            overridings = declaration.actualsForExpect().take(TOOLTIPS_LIMIT).toList()
        }
        if (overridings.isEmpty()) {
            overridings = findFunctionalExpressions(klass)
        }
        val isAbstract = isAbstract(declaration, klass) || declaration.isExpectDeclaration()
        if (overridings.size == TOOLTIPS_LIMIT) {
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
        if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) && !declaration.isEffectivelyActual()) return null
        @OptIn(KaExperimentalApi::class)
        analyze(declaration) {
            var callableSymbol = declaration.symbol as? KaCallableSymbol ?: return null
            if (callableSymbol is KaValueParameterSymbol) {
                callableSymbol = callableSymbol.generatedPrimaryConstructorProperty ?: return null
            }
            val allOverriddenSymbols = callableSymbol.directlyOverriddenSymbols.toList()
            val expectSymbols = callableSymbol.getExpectsForActual()
            if (allOverriddenSymbols.isEmpty() && expectSymbols.isEmpty()) return ""
            val isAbstract = callableSymbol.modality == KaSymbolModality.ABSTRACT
            val abstracts = hashSetOf<PsiElement>()
            val supers = allOverriddenSymbols.mapNotNull {
                val superFunction = it.psi
                if (superFunction != null && it.modality == KaSymbolModality.ABSTRACT) {
                    abstracts.add(superFunction)
                }
                superFunction
            } + expectSymbols.mapNotNull {
                val expectPsi = it.psi
                if (expectPsi != null) abstracts.add(expectPsi)
                expectPsi
            }
            if (supers.isEmpty()) return ""
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