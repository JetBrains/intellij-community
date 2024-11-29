// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.RowIcon
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.idea.KtIconProvider.getBaseIcon
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.implCommon.ActualCompletionLookupElementDecorator
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandler
import org.jetbrains.kotlin.idea.completion.lookups.factories.KotlinFirLookupElementFactory
import org.jetbrains.kotlin.idea.completion.lookups.withAllowedResolve
import org.jetbrains.kotlin.idea.core.overrideImplement.MemberGenerateMode
import org.jetbrains.kotlin.idea.core.overrideImplement.generateMember
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpected
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

@OptIn(KaExperimentalApi::class)
internal class ActualKeywordHandler(
    private val importStrategyDetector: ImportStrategyDetector,
    private val declaration: KtNamedDeclaration? = null,
) : CompletionKeywordHandler<KaSession>(KtTokens.ACTUAL_KEYWORD) {

    context(KaSession)
    override fun createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement> {
        val parameters = KotlinFirCompletionParameters.create(parameters)
            ?: return listOf(lookup)

        return createActualLookups(parameters, project) +
                lookup
    }

    context(KaSession)
    fun createActualLookups(
        parameters: KotlinFirCompletionParameters,
        project: Project
    ): Collection<LookupElement> {
        val position = parameters.position

        // TODO: Allow completion not only for top level actual declarations
        //  `expect`/`actual` interfaces, objects, annotations, enums and typealiases are in Beta
        //  https://youtrack.jetbrains.com/issue/KT-61573
        val isTopLevelDeclaration = position.parent.getNonStrictParentOfType<KtFile>() != null
        if (!isTopLevelDeclaration) return emptyList()

        val module = position.module ?: return emptyList()
        val kaModule = KotlinProjectStructureProvider.getModule(project, position, useSiteModule = null)
        val dependsOnModules = kaModule.transitiveDependsOnDependencies
        if (dependsOnModules.isEmpty()) return emptyList()

        val packageQualifiedName = position.packageDirective?.qualifiedName ?: return emptyList()
        val file = position.containingFile as KtFile

        val notImplementedExpectDeclarations = ExpectActualUtils.collectTopLevelExpectDeclarations(project, dependsOnModules)
            .filter { expectDeclaration -> canImplementActualForExpect(expectDeclaration, module, packageQualifiedName) }

        return notImplementedExpectDeclarations
            .map { expectDeclaration -> expectDeclaration.symbol }
            .filterIsInstance<KaCallableSymbol>()
            .map { expectDeclarationSymbol -> createLookupElement(project, file, expectDeclarationSymbol) }
    }

    private fun canImplementActualForExpect(
        expectDeclaration: KtNamedDeclaration,
        targetModule: Module,
        packageFqnForActual: String,
    ): Boolean {
        val expectDeclarationPackageQualifiedName = expectDeclaration.packageDirective?.qualifiedName ?: return false
        if (expectDeclarationPackageQualifiedName != packageFqnForActual) return false

        val actualsForExpected = expectDeclaration.actualsForExpected(targetModule)
        return actualsForExpected.isEmpty()
    }

    context(KaSession)
    private fun createLookupElement(
        project: Project,
        file: KtFile,
        declarationSymbol: KaCallableSymbol
    ): LookupElement {
        check(declarationSymbol is KaNamedSymbol)

        val baseLookupElement = KotlinFirLookupElementFactory.createLookupElement(
            symbol = declarationSymbol,
            importStrategyDetector = importStrategyDetector,
        )

        val pointer = declarationSymbol.createPointer()

        return ActualCompletionLookupElementDecorator(
            baseLookupElement,
            text = declarationSymbol.textPresentation(),
            icon = declarationSymbol.iconPresentation(),
            baseClassName = null,
            baseClassIcon = null,
            isSuspend = declarationSymbol.isSuspend(),
            generateMember = {
                generateMemberInNewAnalysisSession(project, file, pointer)
            },
            shortenReferences = { element ->
                shortenReferencesInRange(element.containingKtFile, element.textRange)
            },
            declaration = declaration,
        )
    }

    context(KaSession)
    private fun KaDeclarationSymbol.textPresentation(): String {
        val symbol = this
        return buildString {
            append(KtTokens.ACTUAL_KEYWORD.value)
            append(" ")
            append(symbol.render(renderingOptionsForLookupElementRendering))

            if (symbol is KaNamedFunctionSymbol) {
                append(" {...}")
            }
        }
    }

    context(KaSession)
    private fun KaDeclarationSymbol.iconPresentation(): RowIcon {
        val symbol = this
        val baseIcon = getBaseIcon(symbol)
        val additionalIcon = AllIcons.Gutter.ImplementingMethod
        return RowIcon(baseIcon, additionalIcon)
    }

    private fun KaDeclarationSymbol.isSuspend(): Boolean {
        val symbol = this
        return (symbol as? KaNamedFunctionSymbol)?.isSuspend == true
    }

    private fun generateMemberInNewAnalysisSession(
        project: Project,
        file: KtFile,
        symbolPointer: KaSymbolPointer<KaCallableSymbol>,
    ) = withAllowedResolve {
        analyze(file) {
            val symbol = symbolPointer.restoreSymbol()
            requireNotNull(symbol) { "${symbolPointer::class} can't be restored" }

            generateMember(
                project = project,
                ktClassMember = null,
                symbol = symbol,
                targetClass = null,
                copyDoc = false,
                mode = MemberGenerateMode.ACTUAL,
            )
        }
    }

    // Scripts have no package directive, all other files must have package directives
    private val PsiElement.packageDirective: KtPackageDirective?
        get() = (containingFile as? KtFile)?.packageDirective

    companion object {
        @KaExperimentalApi
        private val renderingOptionsForLookupElementRendering = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
            annotationRenderer = annotationRenderer.with {
                annotationFilter = KaRendererAnnotationsFilter.ALL
            }
            modifiersRenderer = modifiersRenderer.with {
                keywordsRenderer = keywordsRenderer.with {
                    keywordFilter = KaRendererKeywordFilter.onlyWith(KtTokens.TYPE_MODIFIER_KEYWORDS)
                }
            }
        }
    }
}