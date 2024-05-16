// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc

import com.google.common.html.HtmlEscapers
import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.KtTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationApplication
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationApplicationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationApplicationWithArgumentsInfo
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtArrayAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtConstantAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtEnumEntryAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtKClassAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtNamedAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtUnsupportedAnnotationValue
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
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KtValueParameterSymbolRenderer
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
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.base.analysis.api.utils.defaultValue
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.parameterInfo.KotlinIdeDescriptorRendererHighlightingManager
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.renderer.render as renderName

internal class KotlinIdeDeclarationRenderer(
    private var highlightingManager: KotlinIdeDescriptorRendererHighlightingManager<KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes> = KotlinIdeDescriptorRendererHighlightingManager.NO_HIGHLIGHTING,
    private val rootSymbol: KtDeclarationSymbol? = null
) {
    context(KtAnalysisSession)
    internal fun renderFunctionTypeParameter(parameter: KtParameter): String? = prettyPrint {
        parameter.nameAsName?.let { name -> withSuffix(highlight(": ") { asColon }) { append(highlight(name.renderName()) { asParameter }) } }
        parameter.typeReference?.getKtType()?.let { type ->
            renderer.typeRenderer.renderType(analysisSession, type, this)
        }

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
        parameterDefaultValueRenderer = object : KtParameterDefaultValueRenderer {
            override fun renderDefaultValue(analysisSession: KtAnalysisSession, symbol: KtValueParameterSymbol, printer: PrettyPrinter) {
                val defaultValue = with(analysisSession) { symbol.defaultValue }
                if (defaultValue != null) {
                    with(highlightingManager) {
                        val builder = StringBuilder()
                        builder.appendCodeSnippetHighlightedByLexer(defaultValue.text)
                        printer.append(builder)
                    }
                }
            }
        }

        valueParameterRenderer = object : KtValueParameterSymbolRenderer {
            override fun renderSymbol(
                analysisSession: KtAnalysisSession,
                symbol: KtValueParameterSymbol,
                declarationRenderer: KtDeclarationRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                highlight(" = ") { asOperationSign }.separated(
                    {
                        callableSignatureRenderer.renderCallableSignature(
                            analysisSession,
                            symbol,
                            keyword = null,
                            declarationRenderer,
                            printer,
                        )
                    },
                    { parameterDefaultValueRenderer.renderDefaultValue(analysisSession, symbol, printer) },
                )
            }
        }

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
            override fun renderKeyword(
                analysisSession: KtAnalysisSession,
                keyword: KtKeywordToken,
                owner: KtAnnotated,
                keywordsRenderer: KtKeywordsRenderer,
                printer: PrettyPrinter
            ) {
                if (keywordFilter.filter(analysisSession, keyword, owner)) {
                    printer.append(highlight(keyword.value) { asKeyword })
                }
            }
        }
    }

    private fun KtAnnotationRenderer.annotationRenderer(): KtAnnotationRenderer = with {
        annotationListRenderer = object : KtAnnotationListRenderer {
            override fun renderAnnotations(
                analysisSession: KtAnalysisSession,
                owner: KtAnnotated,
                annotationRenderer: KtAnnotationRenderer,
                printer: PrettyPrinter
            ) {
                val backingFieldAnnotations = (owner as? KtPropertySymbol)?.backingFieldSymbol?.annotations
                val annotations = (backingFieldAnnotations?.let { owner.annotations + it } ?: owner.annotations).filter {
                    annotationFilter.filter(analysisSession, it, owner)
                }.ifEmpty { return }
                printer.printCollection(
                    annotations, separator = when (owner) {
                        is KtValueParameterSymbol -> " "
                        is KtDeclarationSymbol -> "\n"
                        else -> " "
                    }
                ) { annotation ->
                    append(highlight("@") { asAnnotationName })
                    if (backingFieldAnnotations != null && annotation in backingFieldAnnotations) {
                        printer.append(highlight("field") { asKeyword })
                        printer.append(':')
                    }
                    annotationsQualifiedNameRenderer.renderQualifier(analysisSession, annotation, owner, annotationRenderer, printer)
                    annotationArgumentsRenderer.renderAnnotationArguments(analysisSession, annotation, owner, annotationRenderer, printer)
                }
            }
        }
        annotationArgumentsRenderer = object : KtAnnotationArgumentsRenderer {
            override fun renderAnnotationArguments(
                analysisSession: KtAnalysisSession,
                annotation: KtAnnotationApplication,
                owner: KtAnnotated,
                annotationRenderer: KtAnnotationRenderer,
                printer: PrettyPrinter
            ) {
                if (annotation !is KtAnnotationApplicationWithArgumentsInfo) return

                if (annotation.arguments.isEmpty()) return
                printer.printCollection(annotation.arguments, prefix = "(", postfix = ")") { argument ->
                    append(highlight(argument.name.renderName()) { asParameter })
                    append(highlight(" = ") { asOperationSign } )

                    renderConstantValue(argument.expression)
                }
            }
        }
        annotationsQualifiedNameRenderer = object : KtAnnotationQualifierRenderer {
            override fun renderQualifier(
                analysisSession: KtAnalysisSession,
                annotation: KtAnnotationApplication,
                owner: KtAnnotated,
                annotationRenderer: KtAnnotationRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                val classId = annotation.classId
                if (classId != null) {
                    val buffer = StringBuilder()
                    DocumentationManagerUtil.createHyperlink(
                        buffer,
                        classId.asSingleFqName().asString(),
                        classId.shortClassName.renderName(),
                        true,
                        false
                    )
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
            override fun renderType(
                analysisSession: KtAnalysisSession,
                type: KtFunctionalType,
                typeRenderer: KtTypeRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                if (type.isReflectType) {
                    " ".separated(
                        { typeRenderer.annotationsRenderer.renderAnnotations(analysisSession, type, printer) },
                        {
                            typeRenderer.classIdRenderer.renderClassTypeQualifier(analysisSession, type, typeRenderer, printer)
                            if (type.nullability == KtTypeNullability.NULLABLE) {
                                append(highlight("?") { asNullityMarker })
                            }
                        },
                    )
                    return@printer
                }

                val annotationsRendered = checkIfPrinted {
                    typeRenderer.annotationsRenderer.renderAnnotations(analysisSession, type, this)
                }

                if (annotationsRendered) printer.append(" ")
                if (annotationsRendered || type.nullability == KtTypeNullability.NULLABLE) append(highlight("(") { asParentheses })
                " ".separated(
                    {
                        if (type.isSuspend) {
                            typeRenderer.keywordsRenderer.renderKeyword(analysisSession, KtTokens.SUSPEND_KEYWORD, type, printer)
                        }
                    },
                    {
                        if (type.hasContextReceivers) {
                            typeRenderer.contextReceiversRenderer.renderContextReceivers(analysisSession, type, typeRenderer, printer)
                        }
                    },
                    {
                        type.receiverType?.let {
                            typeRenderer.renderType(analysisSession, it, printer)
                            printer.append(highlight(".") { asDot })
                        }
                        printCollection(type.parameterTypes,
                                        prefix = highlight("(") { asParentheses },
                                        postfix = highlight(") ") { asParentheses }) {
                            typeRenderer.renderType(analysisSession, it, this)
                        }
                        printer.append(highlight("->".escape()) { asArrow }).append(" ")
                        typeRenderer.renderType(analysisSession, type.returnType, printer)
                    },
                )
                if (annotationsRendered || type.nullability == KtTypeNullability.NULLABLE) printer.append(highlight(")") { asParentheses })
                if (type.nullability == KtTypeNullability.NULLABLE) printer.append(highlight("?") { asNullityMarker })
            }
        }
    }

    fun createTypeParameterTypeRenderer(): KtTypeParameterTypeRenderer {
        return object : KtTypeParameterTypeRenderer {
            override fun renderType(
                analysisSession: KtAnalysisSession,
                type: KtTypeParameterType,
                typeRenderer: KtTypeRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                " ".separated({ typeRenderer.annotationsRenderer.renderAnnotations(analysisSession, type, printer) }, {
                    typeRenderer.typeNameRenderer.renderName(analysisSession, type.name, type, typeRenderer, printer)
                    if (type.nullability == KtTypeNullability.NULLABLE) {
                        printer.append(highlight("?") { asNullityMarker })
                    }
                })

            }
        }
    }

    fun createUsualClassTypeRenderer(): KtUsualClassTypeRenderer {
        return object : KtUsualClassTypeRenderer {
            override fun renderType(
                analysisSession: KtAnalysisSession,
                type: KtUsualClassType,
                typeRenderer: KtTypeRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                " ".separated(
                    { typeRenderer.annotationsRenderer.renderAnnotations(analysisSession, type, printer) },
                    {
                        typeRenderer.classIdRenderer.renderClassTypeQualifier(analysisSession, type, typeRenderer, printer)
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
            override fun renderClassTypeQualifier(
                analysisSession: KtAnalysisSession,
                type: KtClassType,
                typeRenderer: KtTypeRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                printCollection(type.qualifiers, separator = highlight(".") { asDot }) { qualifier ->
                    typeRenderer.typeNameRenderer.renderName(analysisSession, qualifier.name, type, typeRenderer, printer)
                    printCollectionIfNotEmpty(qualifier.typeArguments,
                                              prefix = highlight("<".escape()) { asOperationSign },
                                              postfix = highlight(">".escape()) { asOperationSign }) {
                        typeRenderer.typeProjectionRenderer.renderTypeProjection(analysisSession, it, typeRenderer, this)
                    }
                }
            }
        }
    }

    fun createTypeNameRenderer(): KtTypeNameRenderer {
        return object : KtTypeNameRenderer {
            override fun renderName(
                analysisSession: KtAnalysisSession,
                name: Name,
                owner: KtType,
                typeRenderer: KtTypeRenderer,
                printer: PrettyPrinter
            ): Unit = with(analysisSession) {
                if (owner is KtNonErrorClassType) {
                    val superTypes = (owner.expandedClassSymbol as? KtAnonymousObjectSymbol)?.superTypes
                    if (superTypes != null) {
                        printer.append("<".escape())
                        printer.append("anonymous object : ")
                        printer.printCollection(superTypes) {
                            typeRenderer.renderType(analysisSession, it, printer)
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
            override fun renderCallableSignature(
                analysisSession: KtAnalysisSession,
                symbol: KtCallableSymbol,
                keyword: KtKeywordToken?,
                declarationRenderer: KtDeclarationRenderer,
                printer: PrettyPrinter
            ) = with(analysisSession) {
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
                            if (replacedKeyword != null) {
                                renderAnnotationsModifiersAndContextReceivers(analysisSession, callableSymbol, declarationRenderer, printer, replacedKeyword)
                            }
                            else {
                                renderAnnotationsModifiersAndContextReceivers(analysisSession, callableSymbol, declarationRenderer, printer)
                            }
                        },
                        {
                            declarationRenderer.typeParametersRenderer.renderTypeParameters(analysisSession, callableSymbol, declarationRenderer, printer)
                        },
                        {
                            val receiverSymbol = callableSymbol.receiverParameter
                            if (receiverSymbol != null) {
                                withSuffix(highlight(".") { asDot }) {
                                    declarationRenderer.callableReceiverRenderer.renderReceiver(analysisSession, receiverSymbol, declarationRenderer, printer)
                                }
                            }

                            if (callableSymbol is KtNamedSymbol) {
                                declarationRenderer.nameRenderer.renderName(analysisSession, callableSymbol, declarationRenderer, printer)
                            } else if (callableSymbol is KtConstructorSymbol) {
                                (callableSymbol.getContainingSymbol() as? KtNamedSymbol)?.let {
                                    declarationRenderer.nameRenderer.renderName(analysisSession, it, declarationRenderer, printer)
                                }
                            }
                        },
                    )
                    " ".separated(
                        {
                            declarationRenderer.valueParametersRenderer.renderValueParameters(analysisSession, symbol, declarationRenderer, printer)
                            withPrefix(highlight(": ") { asColon }) {
                                declarationRenderer.returnTypeRenderer.renderReturnType(analysisSession, symbol, declarationRenderer, printer)
                            }
                        },
                        {
                            declarationRenderer.typeParametersRenderer.renderWhereClause(analysisSession, symbol, declarationRenderer, printer)
                        },
                    )
                }
            }
        }
    }

    fun createSingleTypeParameterRenderer(): KtSingleTypeParameterSymbolRenderer {
        return object : KtSingleTypeParameterSymbolRenderer {
            override fun renderSymbol(
                analysisSession: KtAnalysisSession,
                symbol: KtTypeParameterSymbol,
                declarationRenderer: KtDeclarationRenderer,
                printer: PrettyPrinter
            ) {
                printer.append(highlight("<".escape()) { asOperationSign })
                printer {
                    " ".separated({ declarationRenderer.annotationRenderer.renderAnnotations(analysisSession, symbol, printer) },
                                  { declarationRenderer.modifiersRenderer.renderDeclarationModifiers(analysisSession, symbol, printer) },
                                  {
                                      declarationRenderer.nameRenderer.renderName(analysisSession, symbol, declarationRenderer, printer)
                                      if (symbol.upperBounds.isNotEmpty()) {
                                          withPrefix(highlight(" : ") { asColon }) {
                                              printCollection(symbol.upperBounds) {
                                                  declarationRenderer.typeRenderer.renderType(
                                                      analysisSession,
                                                      declarationRenderer.declarationTypeApproximator.approximateType(analysisSession, it, Variance.OUT_VARIANCE),
                                                      printer
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
            override fun renderTypeParameters(
                analysisSession: KtAnalysisSession,
                symbol: KtDeclarationSymbol,
                declarationRenderer: KtDeclarationRenderer,
                printer: PrettyPrinter
            ) {
                val typeParameters = symbol.typeParameters
                    .filter { declarationRenderer.typeParametersFilter.filter(analysisSession, it, symbol) }
                    .ifEmpty { return }

                printer.printCollection(typeParameters,
                                        prefix = highlight("<".escape()) { asOperationSign },
                                        postfix = highlight(">".escape()) { asOperationSign }) { typeParameter ->
                    declarationRenderer.codeStyle.getSeparatorBetweenAnnotationAndOwner(analysisSession, typeParameter).separated(
                        { declarationRenderer.annotationRenderer.renderAnnotations(analysisSession, typeParameter, printer) },
                        { declarationRenderer.modifiersRenderer.renderDeclarationModifiers(analysisSession, typeParameter, printer) },
                        { declarationRenderer.nameRenderer.renderName(analysisSession, typeParameter, declarationRenderer, printer) },
                    )
                    if (typeParameter.upperBounds.size == 1) {
                        append(highlight(" : ") { asColon })
                        val ktType = typeParameter.upperBounds.single()
                        val type = declarationRenderer.declarationTypeApproximator
                            .approximateType(analysisSession, ktType, Variance.OUT_VARIANCE)

                        declarationRenderer.typeRenderer.renderType(analysisSession, type, printer)
                    }
                }
            }

            override fun renderWhereClause(
                analysisSession: KtAnalysisSession,
                symbol: KtDeclarationSymbol,
                declarationRenderer: KtDeclarationRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                val allBounds = symbol.typeParameters.filter {
                    declarationRenderer.typeParametersFilter.filter(analysisSession, it, symbol)
                }.flatMap { typeParam ->
                    if (typeParam.upperBounds.size > 1) {
                        typeParam.upperBounds.map { bound -> typeParam to bound }
                    } else {
                        emptyList()
                    }
                }.ifEmpty { return }
                " ".separated(
                    {
                        declarationRenderer.keywordsRenderer.renderKeyword(analysisSession, KtTokens.WHERE_KEYWORD, symbol, printer)
                    },
                    {
                        printer.printCollection(allBounds) { (typeParameter, bound) ->
                            highlight(" : ") { asColon }.separated(
                                {
                                    declarationRenderer.nameRenderer.renderName(analysisSession, typeParameter, declarationRenderer, printer)
                                },
                                {
                                    declarationRenderer.typeRenderer.renderType(
                                        analysisSession,
                                        declarationRenderer.declarationTypeApproximator.approximateType(analysisSession, bound, Variance.OUT_VARIANCE),
                                        printer
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
            override fun renderValueParameters(
                analysisSession: KtAnalysisSession,
                symbol: KtCallableSymbol,
                declarationRenderer: KtDeclarationRenderer,
                printer: PrettyPrinter
            ) {
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
                    declarationRenderer.renderDeclaration(analysisSession, it, printer)
                }
            }
        }
    }

    fun createNameRenderer(): KtDeclarationNameRenderer {
        return object : KtDeclarationNameRenderer {
            override fun renderName(
                analysisSession: KtAnalysisSession,
                name: Name,
                symbol: KtNamedSymbol?,
                declarationRenderer: KtDeclarationRenderer,
                printer: PrettyPrinter
            ): Unit = with(analysisSession) {
                if (symbol is KtClassOrObjectSymbol && symbol.classKind == KtClassKind.COMPANION_OBJECT && symbol.name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) {
                    val className = (symbol.getContainingSymbol() as? KtClassOrObjectSymbol)?.name
                    if (className != null) {
                        printer.append(highlight("of ") { asInfo } )
                        printer.append(highlight(className.renderName()) { asClassName } )
                    }
                    return
                }
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

                if (symbol is KtNamedClassOrObjectSymbol && symbol.isData) {
                    val primaryConstructor = symbol.getDeclaredMemberScope().getConstructors().firstOrNull { it.isPrimary }
                    if (primaryConstructor != null) {
                        declarationRenderer.valueParametersRenderer.renderValueParameters(
                            analysisSession,
                            primaryConstructor,
                            declarationRenderer,
                            printer
                        )
                    }
                }
            }
        }
    }

    fun createReturnTypeRenderer(): KtCallableReturnTypeRenderer {
        return object : KtCallableReturnTypeRenderer {
            override fun renderReturnType(
                analysisSession: KtAnalysisSession,
                symbol: KtCallableSymbol,
                declarationRenderer: KtDeclarationRenderer,
                printer: PrettyPrinter
            ) {
                if (symbol is KtConstructorSymbol) return
                declarationRenderer.typeRenderer.renderType(analysisSession, symbol.returnType, printer)
            }
        }
    }

    private fun PrettyPrinter.renderConstantValue(value: KtAnnotationValue) {
        when (value) {
            is KtAnnotationApplicationValue -> {
                renderAnnotationConstantValue(value)
            }

            is KtArrayAnnotationValue -> {
                renderArrayConstantValue(value)
            }

            is KtEnumEntryAnnotationValue -> {
                renderEnumEntryConstantValue(value)
            }

            is KtConstantAnnotationValue -> {
                renderConstantAnnotationValue(value)
            }

            is KtUnsupportedAnnotationValue -> {
                append("error(\"non-annotation value\")")
            }

            is KtKClassAnnotationValue -> {
                renderKClassAnnotationValue(value)
            }
        }
    }

    private fun PrettyPrinter.renderKClassAnnotationValue(value: KtKClassAnnotationValue) {
        renderType(value.type)
        append("::class")
    }

    private fun PrettyPrinter.renderType(type: KtType) {
        if (type.annotations.isNotEmpty()) {
            for (annotation in type.annotations) {
                append('@')
                renderAnnotationApplication(annotation)
                append(' ')
            }
        }

        when (type) {
            is KtUsualClassType -> {
                val classId = type.classId
                if (classId.isLocal) {
                    append(classId.shortClassName.render())
                } else {
                    append(classId.asSingleFqName().render())
                }

                if (type.ownTypeArguments.isNotEmpty()) {
                    printCollection(type.ownTypeArguments, ", ", prefix = "<", postfix = ">") { typeProjection ->
                        when (typeProjection) {
                            is KtStarTypeProjection -> append('*')
                            is KtTypeArgumentWithVariance -> renderType(typeProjection.type)
                        }
                    }
                }
            }
            is KtClassErrorType -> {
                append("UNRESOLVED_CLASS")
            }
            else -> {
                append(type.asStringForDebugging())
            }
        }
    }

    private fun PrettyPrinter.renderConstantAnnotationValue(value: KtConstantAnnotationValue) {
        with(highlightingManager) {
            val builder = StringBuilder()
            builder.appendCodeSnippetHighlightedByLexer(value.constantValue.renderAsKotlinConstant())
            append(builder)
        }
    }

    private fun PrettyPrinter.renderEnumEntryConstantValue(value: KtEnumEntryAnnotationValue) {
        val callableId = value.callableId
        if (callableId != null) {
            append(highlight(callableId.classId!!.shortClassName.renderName()) { asClassName})
            append(highlight(".") { asDot } )
            append(highlight(callableId.callableName.renderName()) { asClassName })
        }
    }

    private fun PrettyPrinter.renderAnnotationConstantValue(application: KtAnnotationApplicationValue) {
        renderAnnotationApplication(application.annotationValue)
    }

    private fun PrettyPrinter.renderAnnotationApplication(value: KtAnnotationApplicationWithArgumentsInfo) {
        val shortClassName = value.classId?.shortClassName
        if (shortClassName != null) {
            append(highlight("@$shortClassName") {
                asAnnotationName
            })
        }
        if (value.arguments.isNotEmpty()) {
            append(highlight("(") { asBraces })
            renderNamedConstantValueList(value.arguments)
            append(highlight(")") { asBraces })
        }
    }

    private fun PrettyPrinter.renderArrayConstantValue(value: KtArrayAnnotationValue) {
        append(highlight("[") { asBrackets } )
        renderConstantValueList(value.values)
        append(highlight("]") { asBrackets } )
    }

    private fun PrettyPrinter.renderConstantValueList(list: Collection<KtAnnotationValue>) {
        printCollection(list, ", ") { constantValue ->
            renderConstantValue(constantValue)
        }
    }

    private fun PrettyPrinter.renderNamedConstantValueList(list: Collection<KtNamedAnnotationValue>) {
        printCollection(list, ", ") { namedValue ->
            append(highlight(namedValue.name.renderName()) { asParameter } )
            append(highlight(" = ") { asOperationSign } )
            renderConstantValue(namedValue.expression)
        }
    }
}

private fun String.escape(): String = HtmlEscapers.htmlEscaper().escape(this)