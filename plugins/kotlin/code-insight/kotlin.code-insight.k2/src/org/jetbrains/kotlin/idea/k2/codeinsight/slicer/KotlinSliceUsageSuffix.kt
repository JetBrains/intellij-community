// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.slicer

import com.intellij.slicer.SliceUsage
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.renderer.base.KaKeywordsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaParameterDefaultValueRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaConstructorSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaNamedFunctionSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.classifiers.KaNamedClassSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.superTypes.KaSuperTypesFilter
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance

@OptIn(KaExperimentalApi::class)
object KotlinSliceUsageSuffix {
    //private val descriptorRenderer = DescriptorRenderer.ONLY_NAMES_WITH_SHORT_TYPES.withOptions {
    //  withoutReturnType = true
    //  renderConstructorKeyword = false
    //  valueParametersHandler = TruncatedValueParametersHandler(maxParameters = 2)
    //}

    @OptIn(KaExperimentalApi::class)
    @Nls
    fun containerSuffix(sliceUsage: SliceUsage): String? {
        val element = sliceUsage.element ?: return null
        var declaration = element.parents.firstOrNull {
            it is KtClass ||
                    it is KtObjectDeclaration && !it.isObjectLiteral() ||
                    it is KtNamedFunction && !it.isLocal ||
                    it is KtProperty && !it.isLocal ||
                    it is KtPropertyAccessor ||
                    it is KtConstructor<*>
        } as? KtDeclaration ?: return null

        // for a val or var among primary constructor parameters show the class as container
        if (declaration is KtPrimaryConstructor && element is KtParameter && element.hasValOrVar()) {
            declaration = declaration.containingClassOrObject!!
        }

        @Suppress("HardCodedStringLiteral")
        return buildString {
            append(KotlinBundle.message("slicer.text.in", ""))
            append(" ")

            analyze(declaration) {
                val symbol = declaration.symbol


                if (!(symbol is KaCallableSymbol && symbol.isExtension) && symbol !is KaConstructorSymbol && !(symbol is KaClassSymbol && symbol.classKind == KaClassKind.COMPANION_OBJECT)) {
                    val containingClassifier = ((symbol as? KaPropertyAccessorSymbol)?.containingDeclaration ?: symbol).containingDeclaration as? KaClassSymbol
                    if (containingClassifier != null) {
                        append(containingClassifier.render(renderer()))
                        append(".")
                    }
                }

                when (symbol) {
                    is KaPropertySymbol -> {
                        renderPropertyOrAccessor(symbol, null)
                    }

                    is KaPropertyAccessorSymbol -> {
                        renderPropertyOrAccessor((declaration as KtPropertyAccessor).property.symbol, symbol)
                    }

                    else -> {
                        append(symbol.render(renderer()))
                    }
                }
            }
        }
    }

    context(_: KaSession)
    private fun StringBuilder.renderPropertyOrAccessor(callableSymbol: KaCallableSymbol, accessor: KaPropertyAccessorSymbol?) {
        append(callableSymbol.name?.render())
        if (accessor != null) {
            append(".")
            when (accessor) {
                is KaPropertyGetterSymbol -> append("get")
                is KaPropertySetterSymbol -> append("set")
            }
        }
        val receiverType = callableSymbol.receiverType
        if (receiverType != null) {
            append(" on ")
            append(receiverType.render(renderer().typeRenderer, position = Variance.INVARIANT))
        }
    }

    private fun renderer() = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
        returnTypeFilter = object : KaCallableReturnTypeFilter {
            override fun shouldRenderReturnType(analysisSession: KaSession, type: KaType, symbol: KaCallableSymbol): Boolean = false
        }
        keywordsRenderer = KaKeywordsRenderer.AS_WORD.with {
            keywordFilter = KaRendererKeywordFilter.NONE
        }
        superTypesFilter = KaSuperTypesFilter.NONE
        namedClassRenderer = KaNamedClassSymbolRenderer.AS_SOURCE_WITHOUT_PRIMARY_CONSTRUCTOR
        parameterDefaultValueRenderer = KaParameterDefaultValueRenderer.NO_DEFAULT_VALUE
        constructorRenderer = KaConstructorSymbolRenderer.AS_RAW_SIGNATURE
        namedFunctionRenderer = KaNamedFunctionSymbolRenderer.AS_RAW_SIGNATURE
        modifiersRenderer = modifiersRenderer.with {
            keywordsRenderer = keywordsRenderer.with { keywordFilter = KaRendererKeywordFilter.NONE }
        }
    }

    //private class TruncatedValueParametersHandler(private val maxParameters: Int) : DescriptorRenderer.ValueParametersHandler {
    //  private var truncateLength = -1
    //
    //  override fun appendBeforeValueParameters(parameterCount: Int, builder: StringBuilder) {
    //    builder.append("(")
    //  }
    //
    //  override fun appendAfterValueParameters(parameterCount: Int, builder: StringBuilder) {
    //    if (parameterCount > maxParameters) {
    //      builder.setLength(truncateLength)
    //      builder.append(",${Typography.ellipsis}")
    //    }
    //    builder.append(")")
    //  }
    //
    //  override fun appendBeforeValueParameter(
    //    parameter: ValueParameterDescriptor,
    //    parameterIndex: Int,
    //    parameterCount: Int,
    //    builder: StringBuilder
    //  ) {
    //  }
    //
    //  override fun appendAfterValueParameter(
    //    parameter: ValueParameterDescriptor,
    //    parameterIndex: Int,
    //    parameterCount: Int,
    //    builder: StringBuilder
    //  ) {
    //    if (parameterIndex < parameterCount - 1) {
    //      if (parameterIndex == maxParameters - 1) {
    //        truncateLength = builder.length
    //      } else {
    //        builder.append(", ")
    //      }
    //    }
    //  }
    //}

}