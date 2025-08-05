// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc

import com.google.common.html.HtmlEscapers
import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KaNamedAnnotationValue
import org.jetbrains.kotlin.analysis.api.base.KaContextReceiversOwner
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.renderer.base.KaKeywordRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.KaKeywordsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaAnnotationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.renderers.KaAnnotationArgumentsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.renderers.KaAnnotationListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.renderers.KaAnnotationQualifierRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.contextReceivers.KaContextReceiversRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.contextReceivers.renderers.KaContextReceiverListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaRendererTypeApproximator
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaParameterDefaultValueRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaRendererBodyMemberScopeProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.KaDeclarationModifiersRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaModifierListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererModalityModifierProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererOtherModifiersProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererVisibilityModifierProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderAnnotationsModifiersAndContextReceivers
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KaCallableParameterRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KaDeclarationNameRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KaTypeParametersRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaCallableReturnTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaCallableSignatureRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaValueParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.classifiers.KaSingleTypeParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.superTypes.KaSuperTypesCallArgumentsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaContextParameterOwnerSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.analysis.api.useSiteSession
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.idea.base.analysis.api.utils.defaultValue
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.codeinsights.impl.base.parameterInfo.KotlinParameterInfoBase
import org.jetbrains.kotlin.idea.parameterInfo.KotlinIdeDescriptorRendererHighlightingManager
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.renderer.render as renderName

internal class KotlinIdeDeclarationRenderer(
    private var highlightingManager: KotlinIdeDescriptorRendererHighlightingManager<KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes> = KotlinIdeDescriptorRendererHighlightingManager.NO_HIGHLIGHTING,
    private val rootSymbol: KaDeclarationSymbol? = null
) {
    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    internal fun renderFunctionTypeParameter(parameter: KtParameter): String = prettyPrint {
        parameter.nameAsName?.let { name -> withSuffix(highlight(": ") { asColon }) { append(highlight(name.renderName()) { asParameter }) } }
        parameter.typeReference?.type?.let { type ->
            renderer.typeRenderer.renderType(useSiteSession, type, this)
        }

    }

    @KaExperimentalApi
    internal val renderer = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
        nameRenderer = createNameRenderer()
        superTypesArgumentRenderer = KaSuperTypesCallArgumentsRenderer.NO_ARGS
        valueParametersRenderer = createValueParametersRenderer()
        callableSignatureRenderer = createCallableSignatureRenderer()
        singleTypeParameterRenderer = createSingleTypeParameterRenderer()
        typeParametersRenderer = createTypeParametersRenderer()

        returnTypeFilter = KaCallableReturnTypeFilter.ALWAYS
        propertyAccessorsRenderer = KaPropertyAccessorsRenderer.NONE
        bodyMemberScopeProvider = KaRendererBodyMemberScopeProvider.NONE
        parameterDefaultValueRenderer = object : KaParameterDefaultValueRenderer {
            override fun renderDefaultValue(analysisSession: KaSession, symbol: KaValueParameterSymbol, printer: PrettyPrinter) {
                val defaultValue = with(analysisSession) { symbol.defaultValue }
                if (defaultValue != null) {
                    val expressionValue =
                        KotlinParameterInfoBase.getDefaultValueStringRepresentation(defaultValue)
                    with(highlightingManager) {
                        val builder = StringBuilder()
                        if (defaultValue is KtNameReferenceExpression) {
                            val value = defaultValue.text
                            if (expressionValue.isConstValue) {
                                builder.append(highlight(value) { asParameter })
                                builder.append(highlight(" = ") { asOperationSign })
                            }
                        }
                        builder.appendCodeSnippetHighlightedByLexer(expressionValue.text)
                        printer.append(builder)
                    }
                }
            }
        }

        valueParameterRenderer = object : KaValueParameterSymbolRenderer {
            override fun renderSymbol(
                analysisSession: KaSession,
                symbol: KaValueParameterSymbol,
                declarationRenderer: KaDeclarationRenderer,
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
            typeApproximator = KaRendererTypeApproximator.NO_APPROXIMATION
            typeParameterTypeRenderer = createTypeParameterTypeRenderer()
            functionalTypeRenderer = createFunctionalTypeRenderer()
            contextReceiversRenderer = contextReceiversRenderer.with {
                contextReceiverListRenderer = ContextParametersListRendererWithHighlighting()
            }
        }

        contextReceiversRenderer = contextReceiversRenderer.with {
            contextReceiverListRenderer = ContextParametersListRendererWithHighlighting()
        }
    }


    //todo rewrite after KT-66192 is implemented
    @OptIn(KaExperimentalApi::class, KaImplementationDetail::class)
    inner class ContextParametersListRendererWithHighlighting: KaContextReceiverListRenderer {
        override fun renderContextReceivers(
            analysisSession: KaSession,
            owner: KaContextReceiversOwner,
            contextReceiversRenderer: KaContextReceiversRenderer,
            typeRenderer: KaTypeRenderer,
            printer: PrettyPrinter
        ) {
            if (owner is KaContextParameterOwnerSymbol && owner.contextParameters.any { it.psi is KtParameter }) {
                printer {
                    append(highlight("context") { asKeyword })
                    append(highlight("(") { asParentheses } )
                    printCollection(owner.contextParameters) { contextParameter ->

                        append((contextParameter.psi as? KtParameter)?.name ?: contextParameter.name.render())
                        append(highlight(":") { asColon })
                        append(" ")

                        typeRenderer.renderType(analysisSession, contextParameter.returnType, printer)
                    }
                    append(highlight(")") { asParentheses})
                }
            } else {
                val contextReceivers = owner.contextReceivers
                if (contextReceivers.isEmpty()) return

                printer {
                    append(highlight("context") { asKeyword } )
                    append(highlight("(") { asParentheses } )
                    printCollection(contextReceivers) { contextReceiver ->
                        typeRenderer.renderType(analysisSession, contextReceiver.type, printer)
                    }
                    append(highlight(")") { asParentheses } )
                }
            }
        }
    }

    private fun highlight(
        value: String,
        attributesBuilder: KotlinIdeDescriptorRendererHighlightingManager<KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes>.() -> KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes
    ): String {
        return with(highlightingManager) { buildString { appendHighlighted(value, attributesBuilder()) } }
    }

    @KaExperimentalApi
    private fun KaKeywordsRenderer.keywordsRenderer(): KaKeywordsRenderer = with {
        keywordRenderer = object : KaKeywordRenderer {
            override fun renderKeyword(
                analysisSession: KaSession,
                keyword: KtKeywordToken,
                owner: KaAnnotated,
                keywordsRenderer: KaKeywordsRenderer,
                printer: PrettyPrinter
            ) {
                if (keywordFilter.filter(analysisSession, keyword, owner)) {
                    printer.append(highlight(keyword.value) { asKeyword })
                }
            }
        }
    }

    @KaExperimentalApi
    private fun KaAnnotationRenderer.annotationRenderer(): KaAnnotationRenderer = with {
        annotationListRenderer = object : KaAnnotationListRenderer {
            override fun renderAnnotations(
                analysisSession: KaSession,
                owner: KaAnnotated,
                annotationRenderer: KaAnnotationRenderer,
                printer: PrettyPrinter
            ) {
                val backingFieldAnnotations = (owner as? KaPropertySymbol)?.backingFieldSymbol?.annotations
                val annotations = (backingFieldAnnotations?.let { owner.annotations + it } ?: owner.annotations).filter {
                    annotationFilter.filter(analysisSession, it, owner)
                }.ifEmpty { return }
                printer.printCollection(
                    annotations, separator = when (owner) {
                        is KaValueParameterSymbol -> " "
                        is KaDeclarationSymbol -> "\n"
                        else -> " "
                    }
                ) { annotation ->
                    append(highlight("@") { asAnnotationName })
                    (annotation.useSiteTarget?.renderName
                        ?: "field".takeIf { backingFieldAnnotations != null && annotation in backingFieldAnnotations })?.let { useSiteName ->
                            printer.append(highlight(useSiteName) { asKeyword })
                            printer.append(highlight(":") { asColon })
                        }
                    annotationsQualifiedNameRenderer.renderQualifier(analysisSession, annotation, owner, annotationRenderer, printer)
                    annotationArgumentsRenderer.renderAnnotationArguments(analysisSession, annotation, owner, annotationRenderer, printer)
                }
            }
        }
        annotationArgumentsRenderer = object : KaAnnotationArgumentsRenderer {
            override fun renderAnnotationArguments(
                analysisSession: KaSession,
                annotation: KaAnnotation,
                owner: KaAnnotated,
                annotationRenderer: KaAnnotationRenderer,
                printer: PrettyPrinter
            ) {
                if (annotation.arguments.isEmpty()) return
                printer.printCollection(annotation.arguments, prefix = "(", postfix = ")") { argument ->
                    append(highlight(argument.name.renderName()) { asParameter })
                    append(highlight(" = ") { asOperationSign } )

                    renderConstantValue(argument.expression)
                }
            }
        }
        annotationsQualifiedNameRenderer = object : KaAnnotationQualifierRenderer {
            override fun renderQualifier(
                analysisSession: KaSession,
                annotation: KaAnnotation,
                owner: KaAnnotated,
                annotationRenderer: KaAnnotationRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                val classId = annotation.classId
                if (classId != null && !classId.shortClassName.isSpecial) {
                    val buffer = StringBuilder()
                    DocumentationManagerUtil.createHyperlink(
                        buffer,
                        classId.asSingleFqName().asString(),
                        classId.shortClassName.renderName(),
                        true,
                    )
                    printer.append(highlight(buffer.toString()) { asAnnotationName })
                } else {
                    printer.append(highlight(classId?.shortClassName?.renderName()?.escape() ?: "ERROR_ANNOTATION") { asError })
                }
            }
        }
    }

    @KaExperimentalApi
    private fun KaDeclarationModifiersRenderer.modifiersRenderer(): KaDeclarationModifiersRenderer = with {
        visibilityProvider = KaRendererVisibilityModifierProvider.WITH_IMPLICIT_VISIBILITY
        modalityProvider = KaRendererModalityModifierProvider.WITH_IMPLICIT_MODALITY.onlyIf { symbol ->
            when {
                symbol is KaClassSymbol -> !(symbol.classKind == KaClassKind.INTERFACE && symbol.modality == KaSymbolModality.ABSTRACT || symbol.classKind.isObject && symbol.modality == KaSymbolModality.FINAL)

                symbol is KaCallableSymbol -> {
                    symbol.modality == KaSymbolModality.OPEN || symbol.containingDeclaration != null && symbol.modality == KaSymbolModality.FINAL || symbol.modality == KaSymbolModality.ABSTRACT
                }

                else -> false
            }
        }

        fun KaDeclarationSymbol.isInlineClassOrObject(): Boolean = this is KaNamedClassSymbol && isInline

        val valueModifierRenderer = object : KaRendererOtherModifiersProvider {
            override fun getOtherModifiers(analysisSession: KaSession, symbol: KaDeclarationSymbol): List<KtModifierKeywordToken> {
                if (symbol.isInlineClassOrObject()) {
                    (symbol.psi as? KtClass)?.let { klass ->
                        if (klass.hasModifier(KtTokens.INLINE_KEYWORD)) {
                            return listOf(KtTokens.INLINE_KEYWORD)
                        }
                        if (klass.hasModifier(KtTokens.VALUE_KEYWORD)) {
                            return listOf(KtTokens.VALUE_KEYWORD)
                        }
                    }
                }
                if (symbol is KaNamedFunctionSymbol && symbol.isSuspend) {
                    return listOf(KtTokens.SUSPEND_KEYWORD)
                }
                return emptyList()
            }
        }
        otherModifiersProvider = otherModifiersProvider.onlyIf { symbol ->
          !(symbol is KaNamedFunctionSymbol && symbol.isOverride || symbol is KaPropertySymbol && symbol.isOverride) && !symbol.isInlineClassOrObject()
        }.and(valueModifierRenderer)

        modifierListRenderer = object : KaModifierListRenderer {
            override fun renderModifiers(
                analysisSession: KaSession,
                symbol: KaDeclarationSymbol,
                declarationModifiersRenderer: KaDeclarationModifiersRenderer,
                printer: PrettyPrinter
            ) =
                with(analysisSession) {
                    printer {
                        " ".separated(
                            {
                                if (symbol !is KaTypeParameterSymbol && symbol is KaNamedSymbol && symbol.visibility == KaSymbolVisibility.LOCAL) {
                                    printer.append(highlight("local") { asKeyword })
                                }
                            },
                            {
                                KaModifierListRenderer.AS_LIST.renderModifiers(analysisSession, symbol, declarationModifiersRenderer, printer)
                            })
                    }
                }
        }

        keywordsRenderer = keywordsRenderer.keywordsRenderer()
    }

    @KaExperimentalApi
    private fun createFunctionalTypeRenderer(): KaFunctionalTypeRenderer {
        return object : KaFunctionalTypeRenderer {
            override fun renderType(
                analysisSession: KaSession,
                type: KaFunctionType,
                typeRenderer: KaTypeRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                if (type.isReflectType) {
                    " ".separated(
                        { typeRenderer.annotationsRenderer.renderAnnotations(analysisSession, type, printer) },
                        {
                            typeRenderer.classIdRenderer.renderClassTypeQualifier(analysisSession, type, type.qualifiers, typeRenderer, printer)
                            if (type.nullability == KaTypeNullability.NULLABLE) {
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
                if (annotationsRendered || type.nullability == KaTypeNullability.NULLABLE) append(highlight("(") { asParentheses })
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
                        printCollection(type.parameters,
                                        prefix = highlight("(") { asParentheses },
                                        postfix = highlight(") ") { asParentheses }) { valueParameter ->
                            valueParameter.name?.let { name ->
                                typeRenderer.typeNameRenderer.renderName(analysisSession, name, valueParameter.type, typeRenderer, this)
                                append(": ")
                            }
                            typeRenderer.renderType(analysisSession, valueParameter.type, this)
                        }
                        printer.append(highlight("->".escape()) { asArrow }).append(" ")
                        typeRenderer.renderType(analysisSession, type.returnType, printer)
                    },
                )
                if (annotationsRendered || type.nullability == KaTypeNullability.NULLABLE) printer.append(highlight(")") { asParentheses })
                if (type.nullability == KaTypeNullability.NULLABLE) printer.append(highlight("?") { asNullityMarker })
            }
        }
    }

    @KaExperimentalApi
    fun createTypeParameterTypeRenderer(): KaTypeParameterTypeRenderer {
        return object : KaTypeParameterTypeRenderer {
            override fun renderType(
                analysisSession: KaSession,
                type: KaTypeParameterType,
                typeRenderer: KaTypeRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                " ".separated({ typeRenderer.annotationsRenderer.renderAnnotations(analysisSession, type, printer) }, {
                    typeRenderer.typeNameRenderer.renderName(analysisSession, type.name, type, typeRenderer, printer)
                    if (type.nullability == KaTypeNullability.NULLABLE) {
                        printer.append(highlight("?") { asNullityMarker })
                    }
                })

            }
        }
    }

    @KaExperimentalApi
    fun createUsualClassTypeRenderer(): KaUsualClassTypeRenderer {
        return object : KaUsualClassTypeRenderer {
            override fun renderType(
                analysisSession: KaSession,
                type: KaUsualClassType,
                typeRenderer: KaTypeRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                " ".separated(
                    { typeRenderer.annotationsRenderer.renderAnnotations(analysisSession, type, printer) },
                    {
                        typeRenderer.classIdRenderer.renderClassTypeQualifier(analysisSession, type, type.qualifiers, typeRenderer, printer)
                        if (type.nullability == KaTypeNullability.NULLABLE) {
                            append(highlight("?") { asNullityMarker })
                        }
                    },
                )
            }
        }
    }

    @KaExperimentalApi
    fun createClassIdRenderer(): KaClassTypeQualifierRenderer {
        return object : KaClassTypeQualifierRenderer {
            override fun renderClassTypeQualifier(
                analysisSession: KaSession,
                type: KaType,
                qualifiers: List<KaClassTypeQualifier>,
                typeRenderer: KaTypeRenderer,
                printer: PrettyPrinter
            ): Unit = printer {
                printCollection(qualifiers, separator = highlight(".") { asDot }) { qualifier ->
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

    @KaExperimentalApi
    fun createTypeNameRenderer(): KaTypeNameRenderer {
        return object : KaTypeNameRenderer {
            override fun renderName(
                analysisSession: KaSession,
                name: Name,
                owner: KaType,
                typeRenderer: KaTypeRenderer,
                printer: PrettyPrinter
            ): Unit = with(analysisSession) {
                if (owner is KaClassType) {
                    val superTypes = (owner.expandedSymbol as? KaAnonymousObjectSymbol)?.superTypes
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
                    is KaClassType -> owner.classId.asSingleFqName()
                    is KaTypeParameterType -> owner.symbol.containingDeclaration?.getFqNameIfPackageOrNonLocal()?.child(name)
                        ?: FqName.topLevel(name)

                    else -> FqName.topLevel(name)
                }

                val pathSegments = qName.pathSegments()
                val qualifiedName = pathSegments.take(pathSegments.indexOf(name) + 1).joinToString(".")
                val buffer = StringBuilder()
                DocumentationManagerUtil.createHyperlink(buffer, qualifiedName, name.renderName(), true)

                printer.append(highlight(buffer.toString()) { if (owner is KaTypeParameterType) asTypeParameterName else asClassName })
            }
        }
    }

    @KaExperimentalApi
    fun createCallableSignatureRenderer(): KaCallableSignatureRenderer {
        return object : KaCallableSignatureRenderer {
            override fun renderCallableSignature(
                analysisSession: KaSession,
                symbol: KaCallableSymbol,
                keyword: KtKeywordToken?,
                declarationRenderer: KaDeclarationRenderer,
                printer: PrettyPrinter
            ) = with(analysisSession) {
                printer {
                    val callableSymbol = (symbol as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty ?: symbol
                    " ".separated(
                        {
                            val replacedKeyword = when {
                                keyword != null -> keyword
                              callableSymbol is KaPropertySymbol -> if (callableSymbol.isVal) KtTokens.VAL_KEYWORD else KtTokens.VAR_KEYWORD
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
                                    val isFunctional = receiverSymbol.returnType is KaFunctionType
                                    if (isFunctional) {
                                        append(highlight("(") { asParentheses })
                                    }
                                    declarationRenderer.callableReceiverRenderer.renderReceiver(analysisSession, receiverSymbol, declarationRenderer, printer)
                                    if (isFunctional) {
                                        append(highlight(")") { asParentheses })
                                    }
                                }
                            }

                            if (callableSymbol is KaNamedSymbol) {
                                declarationRenderer.nameRenderer.renderName(analysisSession, callableSymbol, declarationRenderer, printer)
                            } else if (callableSymbol is KaConstructorSymbol) {
                                (callableSymbol.containingDeclaration as? KaNamedSymbol)?.let {
                                    printer.append(highlight(it.name.renderName()) {
                                        asClassName
                                    })
                                }
                            }
                        },
                    )
                    " ".separated(
                        {
                            declarationRenderer.valueParametersRenderer.renderValueParameters(analysisSession, symbol, declarationRenderer, printer)
                            withPrefix(highlight(": ") { asColon }) {
                                declarationRenderer.returnTypeRenderer.renderReturnType(analysisSession, callableSymbol, declarationRenderer, printer)
                            }
                        },
                        {
                            declarationRenderer.typeParametersRenderer.renderWhereClause(analysisSession, symbol, declarationRenderer, printer)
                        },
                    )
                    if (symbol is KaPropertySymbol) {
                        symbol.initializer?.initializerPsi?.let {
                            val builder = StringBuilder()
                            with(highlightingManager) {
                                builder.append(highlight(" = ") { asOperationSign })
                                val expressionValue = KotlinParameterInfoBase.getDefaultValueStringRepresentation(it)
                                builder.appendCodeSnippetHighlightedByLexer(expressionValue.text)
                            }
                            printer.append(builder)
                        }
                    }
                }
            }
        }
    }

    @KaExperimentalApi
    fun createSingleTypeParameterRenderer(): KaSingleTypeParameterSymbolRenderer {
        return object : KaSingleTypeParameterSymbolRenderer {
            override fun renderSymbol(
                analysisSession: KaSession,
                symbol: KaTypeParameterSymbol,
                declarationRenderer: KaDeclarationRenderer,
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

    @KaExperimentalApi
    fun createTypeParametersRenderer(): KaTypeParametersRenderer {
        return object : KaTypeParametersRenderer {
            override fun renderTypeParameters(
                analysisSession: KaSession,
                symbol: KaDeclarationSymbol,
                declarationRenderer: KaDeclarationRenderer,
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
                analysisSession: KaSession,
                symbol: KaDeclarationSymbol,
                declarationRenderer: KaDeclarationRenderer,
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

    @KaExperimentalApi
    fun createValueParametersRenderer(): KaCallableParameterRenderer {
        return object : KaCallableParameterRenderer {
            override fun renderValueParameters(
                analysisSession: KaSession,
                symbol: KaCallableSymbol,
                declarationRenderer: KaDeclarationRenderer,
                printer: PrettyPrinter
            ) {
                val valueParameters = when (symbol) {
                    is KaFunctionSymbol -> symbol.valueParameters
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

    @KaExperimentalApi
    fun createNameRenderer(): KaDeclarationNameRenderer {
        return object : KaDeclarationNameRenderer {
            override fun renderName(
                analysisSession: KaSession,
                name: Name,
                symbol: KaNamedSymbol?,
                declarationRenderer: KaDeclarationRenderer,
                printer: PrettyPrinter
            ): Unit = with(analysisSession) {
                if (symbol is KaClassSymbol && symbol.classKind == KaClassKind.COMPANION_OBJECT && symbol.name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) {
                    val className = (symbol.containingDeclaration as? KaClassSymbol)?.name
                    if (className != null) {
                        printer.append(highlight("of ") { asInfo } )
                        printer.append(highlight(className.renderName()) { asClassName } )
                    }
                    return
                }
                if (symbol is KaEnumEntrySymbol) {
                    printer.append(highlight("enum entry") { asKeyword })
                    printer.append(" ")
                }
                printer.append(highlight(name.renderName()) {
                    when (symbol) {
                        is KaClassSymbol -> {
                            if (symbol.classKind.isObject) {
                                asObjectName
                            } else if (symbol.classKind == KaClassKind.ENUM_CLASS || symbol.classKind == KaClassKind.CLASS || symbol.classKind == KaClassKind.INTERFACE) {
                                asClassName
                            } else {
                                asAnnotationName
                            }
                        }

                        is KaParameterSymbol -> asParameter
                        is KaPackageSymbol -> asPackageName
                        is KaTypeParameterSymbol -> asTypeParameterName
                        is KaTypeAliasSymbol -> asTypeAlias
                        is KaPropertySymbol -> asInstanceProperty
                        else -> asFunDeclaration
                    }
                })

                if (symbol is KaNamedClassSymbol && symbol.isData) {
                    val primaryConstructor = symbol.declaredMemberScope.constructors.firstOrNull { it.isPrimary }
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

    @KaExperimentalApi
    fun createReturnTypeRenderer(): KaCallableReturnTypeRenderer {
        return object : KaCallableReturnTypeRenderer {
            override fun renderReturnType(
                analysisSession: KaSession,
                symbol: KaCallableSymbol,
                declarationRenderer: KaDeclarationRenderer,
                printer: PrettyPrinter
            ) {
                if (symbol is KaConstructorSymbol) return
                declarationRenderer.typeRenderer.renderType(analysisSession, symbol.returnType, printer)
            }
        }
    }

    private fun PrettyPrinter.renderConstantValue(value: KaAnnotationValue) {
        when (value) {
            is KaAnnotationValue.NestedAnnotationValue -> {
                renderAnnotationConstantValue(value)
            }

            is KaAnnotationValue.ArrayValue -> {
                renderArrayConstantValue(value)
            }

            is KaAnnotationValue.EnumEntryValue -> {
                renderEnumEntryConstantValue(value)
            }

            is KaAnnotationValue.ConstantValue -> {
                renderConstantAnnotationValue(value)
            }

            is KaAnnotationValue.UnsupportedValue -> {
                append("error(\"non-annotation value\")")
            }

            is KaAnnotationValue.ClassLiteralValue -> {
                renderKClassAnnotationValue(value)
            }
        }
    }

    private fun PrettyPrinter.renderKClassAnnotationValue(value: KaAnnotationValue.ClassLiteralValue) {
        renderType(value.type)
        append("::class")
    }

    private fun PrettyPrinter.renderType(type: KaType) {
        if (type.annotations.isNotEmpty()) {
            for (annotation in type.annotations) {
                append('@')
                renderAnnotationApplication(annotation)
                append(' ')
            }
        }

        when (type) {
            is KaUsualClassType -> {
                val classId = type.classId
                if (classId.isLocal) {
                    append(classId.shortClassName.render())
                } else {
                    append(classId.asSingleFqName().render())
                }

                if (type.typeArguments.isNotEmpty()) {
                    printCollection(type.typeArguments, ", ", prefix = "<", postfix = ">") { typeProjection ->
                        when (typeProjection) {
                            is KaStarTypeProjection -> append('*')
                            is KaTypeArgumentWithVariance -> renderType(typeProjection.type)
                        }
                    }
                }
            }
            is KaClassErrorType -> {
                append("UNRESOLVED_CLASS")
            }
            else -> {
                append(type.toString())
            }
        }
    }

    private fun PrettyPrinter.renderConstantAnnotationValue(value: KaAnnotationValue.ConstantValue) {
        with(highlightingManager) {
            val builder = StringBuilder()
            builder.appendCodeSnippetHighlightedByLexer(value.value.render())
            append(builder)
        }
    }

    private fun PrettyPrinter.renderEnumEntryConstantValue(value: KaAnnotationValue.EnumEntryValue) {
        val callableId = value.callableId
        if (callableId != null) {
            append(highlight(callableId.classId!!.shortClassName.renderName()) { asClassName})
            append(highlight(".") { asDot } )
            append(highlight(callableId.callableName.renderName()) { asClassName })
        }
    }

    private fun PrettyPrinter.renderAnnotationConstantValue(application: KaAnnotationValue.NestedAnnotationValue) {
        renderAnnotationApplication(application.annotation)
    }

    private fun PrettyPrinter.renderAnnotationApplication(value: KaAnnotation) {
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

    private fun PrettyPrinter.renderArrayConstantValue(value: KaAnnotationValue.ArrayValue) {
        append(highlight("[") { asBrackets } )
        renderConstantValueList(value.values)
        append(highlight("]") { asBrackets } )
    }

    private fun PrettyPrinter.renderConstantValueList(list: Collection<KaAnnotationValue>) {
        printCollection(list, ", ") { constantValue ->
            renderConstantValue(constantValue)
        }
    }

    private fun PrettyPrinter.renderNamedConstantValueList(list: Collection<KaNamedAnnotationValue>) {
        printCollection(list, ", ") { namedValue ->
            append(highlight(namedValue.name.renderName()) { asParameter } )
            append(highlight(" = ") { asOperationSign } )
            renderConstantValue(namedValue.expression)
        }
    }
}

private fun String.escape(): String = HtmlEscapers.htmlEscaper().escape(this)
