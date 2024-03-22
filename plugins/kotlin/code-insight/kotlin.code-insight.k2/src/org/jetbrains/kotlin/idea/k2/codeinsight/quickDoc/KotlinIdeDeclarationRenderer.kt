// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc

import com.google.common.html.HtmlEscapers
import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationApplication
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.renderer.base.KtKeywordRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.KtKeywordsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KtAnnotationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.renderers.KtAnnotationArgumentsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.renderers.KtAnnotationListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.renderers.KtAnnotationQualifierRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtRendererTypeApproximator
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KtParameterDefaultValueRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KtRendererBodyMemberScopeProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.KtDeclarationModifiersRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererModalityModifierProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererVisibilityModifierProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderAnnotationsModifiersAndContextReceivers
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KtCallableParameterRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KtDeclarationNameRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KtTypeParametersRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KtCallableReturnTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KtCallableSignatureRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KtPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.classifiers.KtSingleTypeParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.KtTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtClassTypeQualifierRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtFunctionalTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtTypeNameRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtTypeParameterTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtUsualClassTypeRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.KtClassType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.parameterInfo.KotlinIdeDescriptorRendererHighlightingManager
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.renderer.render as renderName

internal class KotlinIdeDeclarationRenderer(
    private var highlightingManager: KotlinIdeDescriptorRendererHighlightingManager<KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes> = KotlinIdeDescriptorRendererHighlightingManager.NO_HIGHLIGHTING,
    private val rootSymbol: KtDeclarationSymbol? = null
) {
    context(KtAnalysisSession)
    internal fun renderFunctionTypeParameter(parameter: KtParameter): String? = prettyPrint {
        parameter.nameAsName?.let { name -> withSuffix(highlight(": ") { asColon }) { append(highlight(name.renderName()) { asParameter }) } }
        parameter.typeReference?.getKtType()?.let { type -> renderer.typeRenderer.renderType(type, this) }

    }

    internal val renderer = KtDeclarationRendererForSource.WITH_SHORT_NAMES.with {
        nameRenderer = createNameRenderer()
        valueParametersRenderer = createValueParametersRenderer()
        callableSignatureRenderer = createCallableSignatureRenderer()
        singleTypeParameterRenderer = createSingleTypeParameterRenderer()
        typeParametersRenderer = createTypeParametersRenderer()

        returnTypeFilter = KtCallableReturnTypeFilter.ALWAYS
        propertyAccessorsRenderer = KtPropertyAccessorsRenderer.NONE
        bodyMemberScopeProvider = KtRendererBodyMemberScopeProvider.NONE
        parameterDefaultValueRenderer = KtParameterDefaultValueRenderer.THREE_DOTS

        keywordsRenderer = keywordsRenderer.keywordsRenderer()
        modifiersRenderer = modifiersRenderer.modifiersRenderer()
        annotationRenderer = annotationRenderer.annotationRenderer()

        returnTypeRenderer = createReturnTypeRenderer()
        typeRenderer = typeRenderer.with {
            annotationsRenderer = annotationsRenderer.annotationRenderer()
            keywordsRenderer = keywordsRenderer.keywordsRenderer()
            typeNameRenderer = createTypeNameRenderer()
            classIdRenderer = createClassIdRenderer()
            usualClassTypeRenderer = createUsualClassTypeRenderer()
            typeApproximator = KtRendererTypeApproximator.NO_APPROXIMATION
            typeParameterTypeRenderer = createTypeParameterTypeRenderer()
            functionalTypeRenderer = createFunctionalTypeRenderer()
        }
    }

    private fun highlight(
        value: String,
        attributesBuilder: KotlinIdeDescriptorRendererHighlightingManager<KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes>.() -> KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes
    ): String {
        return with(highlightingManager) { buildString { appendHighlighted(value, attributesBuilder()) } }
    }

    private fun KtKeywordsRenderer.keywordsRenderer(): KtKeywordsRenderer = with {
        keywordRenderer = object : KtKeywordRenderer {
            context(KtAnalysisSession, KtKeywordsRenderer)
            override fun renderKeyword(
                keyword: KtKeywordToken, owner: KtAnnotated, printer: PrettyPrinter
            ) {
                if (keywordFilter.filter(keyword, owner)) {
                    printer.append(highlight(keyword.value) { asKeyword })
                }
            }
        }
    }

    private fun KtAnnotationRenderer.annotationRenderer(): KtAnnotationRenderer = with {
        annotationListRenderer = object : KtAnnotationListRenderer {
            context(KtAnalysisSession, KtAnnotationRenderer)
            override fun renderAnnotations(owner: KtAnnotated, printer: PrettyPrinter) {
                val annotations = owner.annotations.filter { annotationFilter.filter(it, owner) }.ifEmpty { return }
                printer.printCollection(
                    annotations, separator = when (owner) {
                        is KtValueParameterSymbol -> " "
                        is KtDeclarationSymbol -> "\n"
                        else -> " "
                    }
                ) { annotation ->
                    append(highlight("@") { asAnnotationName })
                    annotationUseSiteTargetRenderer.renderUseSiteTarget(annotation, owner, printer)
                    annotationsQualifiedNameRenderer.renderQualifier(annotation, owner, printer)
                    annotationArgumentsRenderer.renderAnnotationArguments(annotation, owner, printer)
                }
            }
        }
        annotationArgumentsRenderer = KtAnnotationArgumentsRenderer.NONE
        annotationsQualifiedNameRenderer = object : KtAnnotationQualifierRenderer {
            context(KtAnalysisSession, KtAnnotationRenderer)
            override fun renderQualifier(
                annotation: KtAnnotationApplication, owner: KtAnnotated, printer: PrettyPrinter
            ): Unit = printer {
                val classId = annotation.classId
                if (classId != null) {
                    val buffer = StringBuilder()
                    DocumentationManagerUtil.createHyperlink(buffer, classId.asString(), classId.shortClassName.renderName(), true, false)
                    printer.append(highlight(buffer.toString()) { asAnnotationName })
                } else {
                    printer.append(highlight("ERROR_ANNOTATION") { asError })
                }
            }
        }
    }

    private fun KtDeclarationModifiersRenderer.modifiersRenderer(): KtDeclarationModifiersRenderer = with {
        visibilityProvider = KtRendererVisibilityModifierProvider.WITH_IMPLICIT_VISIBILITY
        modalityProvider = KtRendererModalityModifierProvider.WITH_IMPLICIT_MODALITY.onlyIf { symbol ->
            when {
                symbol is KtClassOrObjectSymbol -> !(symbol.classKind == KtClassKind.INTERFACE && symbol.modality == Modality.ABSTRACT || symbol.classKind.isObject && symbol.modality == Modality.FINAL)

                symbol is KtCallableSymbol -> {
                    symbol.modality == Modality.OPEN || symbol.getContainingSymbol() != null && symbol.modality == Modality.FINAL || symbol.modality == Modality.ABSTRACT
                }

                else -> false
            }
        }
        otherModifiersProvider = otherModifiersProvider.onlyIf { symbol ->
            !(symbol is KtFunctionSymbol && symbol.isOverride || symbol is KtPropertySymbol && symbol.isOverride)
        }
        keywordsRenderer = keywordsRenderer.keywordsRenderer()
    }

    private fun createFunctionalTypeRenderer(): KtFunctionalTypeRenderer {
        return object : KtFunctionalTypeRenderer {
            context(KtAnalysisSession, KtTypeRenderer)
            override fun renderType(type: KtFunctionalType, printer: PrettyPrinter): Unit = printer {
                if (type.isReflectType) {
                    " ".separated(
                        { annotationsRenderer.renderAnnotations(type, printer) },
                        {
                            classIdRenderer.renderClassTypeQualifier(type, printer)
                            if (type.nullability == KtTypeNullability.NULLABLE) {
                                append(highlight("?") { asNullityMarker })
                            }
                        },
                    )
                    return@printer
                }
                val annotationsRendered = checkIfPrinted { annotationsRenderer.renderAnnotations(type, this) }
                if (annotationsRendered) printer.append(" ")
                if (annotationsRendered || type.nullability == KtTypeNullability.NULLABLE) append(highlight("(") { asParentheses })
                " ".separated(
                    {
                        if (type.isSuspend) {
                            keywordsRenderer.renderKeyword(KtTokens.SUSPEND_KEYWORD, type, printer)
                        }
                    },
                    {
                        if (type.hasContextReceivers) {
                            contextReceiversRenderer.renderContextReceivers(type, printer)
                        }
                    },
                    {
                        type.receiverType?.let {
                            renderType(it, printer)
                            printer.append(highlight(".") { asDot })
                        }
                        printCollection(type.parameterTypes,
                                        prefix = highlight("(") { asParentheses },
                                        postfix = highlight(") ") { asParentheses }) {
                            renderType(it, this)
                        }
                        printer.append(highlight("->".escape()) { asArrow }).append(" ")
                        renderType(type.returnType, printer)
                    },
                )
                if (annotationsRendered || type.nullability == KtTypeNullability.NULLABLE) printer.append(highlight(")") { asParentheses })
                if (type.nullability == KtTypeNullability.NULLABLE) printer.append(highlight("?") { asNullityMarker })
            }
        }
    }

    fun createTypeParameterTypeRenderer(): KtTypeParameterTypeRenderer {
        return object : KtTypeParameterTypeRenderer {
            context(KtAnalysisSession, KtTypeRenderer)
            override fun renderType(
                type: KtTypeParameterType, printer: PrettyPrinter
            ) = printer {
                " ".separated({ annotationsRenderer.renderAnnotations(type, printer) }, {
                    typeNameRenderer.renderName(type.name, type, printer)
                    if (type.nullability == KtTypeNullability.NULLABLE) {
                        printer.append(highlight("?") { asNullityMarker })
                    }
                })

            }
        }
    }

    fun createUsualClassTypeRenderer(): KtUsualClassTypeRenderer {
        return object : KtUsualClassTypeRenderer {
            context(KtAnalysisSession, KtTypeRenderer)
            override fun renderType(type: KtUsualClassType, printer: PrettyPrinter): Unit = printer {
                " ".separated(
                    { annotationsRenderer.renderAnnotations(type, printer) },
                    {
                        classIdRenderer.renderClassTypeQualifier(type, printer)
                        if (type.nullability == KtTypeNullability.NULLABLE) {
                            append(highlight("?") { asNullityMarker })
                        }
                    },
                )
            }
        }
    }

    fun createClassIdRenderer(): KtClassTypeQualifierRenderer {
        return object : KtClassTypeQualifierRenderer {
            context(KtAnalysisSession, KtTypeRenderer)
            override fun renderClassTypeQualifier(type: KtClassType, printer: PrettyPrinter): Unit = printer {
                printCollection(type.qualifiers, separator = highlight(".") { asDot }) { qualifier ->
                    typeNameRenderer.renderName(qualifier.name, type, printer)
                    printCollectionIfNotEmpty(qualifier.typeArguments,
                                              prefix = highlight("<".escape()) { asOperationSign },
                                              postfix = highlight(">".escape()) { asOperationSign }) {
                        typeProjectionRenderer.renderTypeProjection(it, this)
                    }
                }
            }
        }
    }

    fun createTypeNameRenderer(): KtTypeNameRenderer {
        return object : KtTypeNameRenderer {
            context(KtAnalysisSession, KtTypeRenderer)
            override fun renderName(name: Name, owner: KtType, printer: PrettyPrinter) {
                if (owner is KtNonErrorClassType) {
                    val superTypes = (owner.expandedClassSymbol as? KtAnonymousObjectSymbol)?.superTypes
                    if (superTypes != null) {
                        printer.append("<".escape())
                        printer.append("anonymous object : ")
                        printer.printCollection(superTypes) {
                            renderType(it, printer)
                        }
                        printer.append(">".escape())
                        return
                    }
                }
                val qName = when (owner) {
                    is KtNonErrorClassType -> owner.classId.asSingleFqName()
                    is KtTypeParameterType -> owner.symbol.getContainingSymbol()?.getFqNameIfPackageOrNonLocal()?.child(name)
                        ?: FqName.topLevel(name)

                    else -> FqName.topLevel(name)
                }
                val text = if (qName.parent() == FqName.topLevel(StandardNames.BUILT_INS_PACKAGE_NAME)) name.renderName() else {
                    val buffer = StringBuilder()
                    DocumentationManagerUtil.createHyperlink(buffer, qName.asString(), name.renderName(), true, false)
                    buffer.toString()
                }

                printer.append(highlight(text) { if (owner is KtTypeParameterType) asTypeParameterName else asClassName })
            }
        }
    }

    fun createCallableSignatureRenderer(): KtCallableSignatureRenderer {
        return object : KtCallableSignatureRenderer {
            context(KtAnalysisSession, KtDeclarationRenderer)
            override fun renderCallableSignature(symbol: KtCallableSymbol, keyword: KtKeywordToken?, printer: PrettyPrinter): Unit =
                printer {
                    val callableSymbol = (symbol as? KtValueParameterSymbol)?.generatedPrimaryConstructorProperty ?: symbol
                    " ".separated(
                        {
                            if (symbol is KtValueParameterSymbol && symbol == rootSymbol && callableSymbol == symbol) {
                                printer.append(highlight("value-parameter") { asKeyword })
                            }
                        },
                        {
                            if (callableSymbol is KtSymbolWithVisibility && callableSymbol.visibility == Visibilities.Local) {
                                printer.append(highlight("local") { asKeyword })
                            }
                        },
                        {
                            val replacedKeyword = when {
                                keyword != null -> keyword
                                callableSymbol is KtPropertySymbol -> if (callableSymbol.isVal) KtTokens.VAL_KEYWORD else KtTokens.VAR_KEYWORD
                                else -> null
                            }
                            if (replacedKeyword != null) renderAnnotationsModifiersAndContextReceivers(
                                callableSymbol, printer, replacedKeyword
                            )
                            else renderAnnotationsModifiersAndContextReceivers(callableSymbol, printer)
                        },
                        { typeParametersRenderer.renderTypeParameters(callableSymbol, printer) },
                        {
                            val receiverSymbol = callableSymbol.receiverParameter
                            if (receiverSymbol != null) {
                                withSuffix(highlight(".") { asDot }) { callableReceiverRenderer.renderReceiver(receiverSymbol, printer) }
                            }

                            if (callableSymbol is KtNamedSymbol) {
                                nameRenderer.renderName(callableSymbol, printer)
                            } else if (callableSymbol is KtConstructorSymbol) {
                                (callableSymbol.getContainingSymbol() as? KtNamedSymbol)?.let {
                                    nameRenderer.renderName(it, printer)
                                }
                            }
                        },
                    )
                    " ".separated(
                        {
                            valueParametersRenderer.renderValueParameters(symbol, printer)
                            withPrefix(highlight(": ") { asColon }) { returnTypeRenderer.renderReturnType(symbol, printer) }
                        },
                        { typeParametersRenderer.renderWhereClause(symbol, printer) },
                    )
                }
        }
    }

    fun createSingleTypeParameterRenderer(): KtSingleTypeParameterSymbolRenderer {
        return object : KtSingleTypeParameterSymbolRenderer {
            context(KtAnalysisSession, KtDeclarationRenderer)
            override fun renderSymbol(
                symbol: KtTypeParameterSymbol, printer: PrettyPrinter
            ) {
                printer.append(highlight("<".escape()) { asOperationSign })
                printer {
                    " ".separated({ annotationRenderer.renderAnnotations(symbol, printer) },
                                  { modifiersRenderer.renderDeclarationModifiers(symbol, printer) },
                                  {
                                      nameRenderer.renderName(symbol, printer)
                                      if (symbol.upperBounds.isNotEmpty()) {
                                          withPrefix(highlight(" : ") { asColon }) {
                                              printCollection(symbol.upperBounds) {
                                                  typeRenderer.renderType(
                                                      declarationTypeApproximator.approximateType(it, Variance.OUT_VARIANCE), printer
                                                  )
                                              }
                                          }
                                      }
                                  })
                }
                printer.append(highlight(">".escape()) { asOperationSign })
            }
        }
    }

    fun createTypeParametersRenderer(): KtTypeParametersRenderer {
        return object : KtTypeParametersRenderer {
            context(KtAnalysisSession, KtDeclarationRenderer)
            override fun renderTypeParameters(symbol: KtDeclarationSymbol, printer: PrettyPrinter) {
                val typeParameters = symbol.typeParameters.filter { typeParametersFilter.filter(it, symbol) }.ifEmpty { return }
                printer.printCollection(typeParameters,
                                        prefix = highlight("<".escape()) { asOperationSign },
                                        postfix = highlight(">".escape()) { asOperationSign }) { typeParameter ->
                    codeStyle.getSeparatorBetweenAnnotationAndOwner(typeParameter).separated(
                        { annotationRenderer.renderAnnotations(typeParameter, printer) },
                        { modifiersRenderer.renderDeclarationModifiers(typeParameter, printer) },
                        { nameRenderer.renderName(typeParameter, printer) },
                    )
                    if (typeParameter.upperBounds.size == 1) {
                        append(highlight(" : ") { asColon })
                        val ktType = typeParameter.upperBounds.single()
                        val type = declarationTypeApproximator.approximateType(ktType, Variance.OUT_VARIANCE)
                        typeRenderer.renderType(type, printer)
                    }
                }
            }

            context(KtAnalysisSession, KtDeclarationRenderer)
            override fun renderWhereClause(symbol: KtDeclarationSymbol, printer: PrettyPrinter): Unit = printer {
                val allBounds = symbol.typeParameters.filter { typeParametersFilter.filter(it, symbol) }.flatMap { typeParam ->
                    if (typeParam.upperBounds.size > 1) {
                        typeParam.upperBounds.map { bound -> typeParam to bound }
                    } else {
                        emptyList()
                    }
                }.ifEmpty { return }
                " ".separated(
                    {
                        keywordsRenderer.renderKeyword(KtTokens.WHERE_KEYWORD, symbol, printer)
                    },
                    {
                        printer.printCollection(allBounds) { (typeParameter, bound) ->
                            highlight(" : ") { asColon }.separated(
                                { nameRenderer.renderName(typeParameter, printer) },
                                {
                                    typeRenderer.renderType(
                                        declarationTypeApproximator.approximateType(bound, Variance.OUT_VARIANCE), printer
                                    )
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    fun createValueParametersRenderer(): KtCallableParameterRenderer {
        return object : KtCallableParameterRenderer {
            context(KtAnalysisSession, KtDeclarationRenderer)
            override fun renderValueParameters(symbol: KtCallableSymbol, printer: PrettyPrinter) {
                val valueParameters = when (symbol) {
                    is KtFunctionLikeSymbol -> symbol.valueParameters
                    else -> return
                }
                if (valueParameters.isEmpty()) {
                    printer.append("()")
                    return
                }
                printer.printCollection(
                    valueParameters, prefix = "(\n    ", postfix = "\n)", separator = ",\n    "
                ) {
                    renderDeclaration(it, printer)
                }
            }
        }
    }

    fun createNameRenderer(): KtDeclarationNameRenderer {
        return object : KtDeclarationNameRenderer {
            context(KtAnalysisSession, KtDeclarationRenderer)
            override fun renderName(name: Name, symbol: KtNamedSymbol?, printer: PrettyPrinter) {
                if (symbol is KtClassOrObjectSymbol && symbol.classKind == KtClassKind.COMPANION_OBJECT && symbol.name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) return
                if (symbol is KtEnumEntrySymbol) {
                    printer.append(highlight("enum entry") { asKeyword })
                    printer.append(" ")
                }
                printer.append(highlight(name.renderName()) {
                    when (symbol) {
                        is KtClassOrObjectSymbol -> {
                            if (symbol.classKind.isObject) {
                                asObjectName
                            } else if (symbol.classKind == KtClassKind.ENUM_CLASS || symbol.classKind == KtClassKind.CLASS || symbol.classKind == KtClassKind.INTERFACE) {
                                asClassName
                            } else {
                                asAnnotationName
                            }
                        }

                        is KtParameterSymbol -> asParameter
                        is KtPackageSymbol -> asPackageName
                        is KtTypeParameterSymbol -> asTypeParameterName
                        is KtTypeAliasSymbol -> asTypeAlias
                        is KtPropertySymbol -> asInstanceProperty
                        else -> asFunDeclaration
                    }
                })
            }
        }
    }

    fun createReturnTypeRenderer(): KtCallableReturnTypeRenderer {
        return object : KtCallableReturnTypeRenderer {
            context(KtAnalysisSession, KtDeclarationRenderer)
            override fun renderReturnType(symbol: KtCallableSymbol, printer: PrettyPrinter) {
                if (symbol is KtConstructorSymbol) return
                typeRenderer.renderType(symbol.returnType, printer)
            }
        }
    }
}

private fun String.escape(): String = HtmlEscapers.htmlEscaper().escape(this)