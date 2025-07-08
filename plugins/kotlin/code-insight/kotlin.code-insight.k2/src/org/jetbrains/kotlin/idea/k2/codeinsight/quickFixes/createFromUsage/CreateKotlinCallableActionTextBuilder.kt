// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.util.text.UniqueNameGenerator
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaClassTypeQualifierRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaClassTypeQualifier
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.cleanupRenderedType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction.ParamCandidate
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.convertToClass
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.hasAbstractDeclaration
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.toKtTypeWithNullability
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.types.Variance

/**
 * A class to build an IntentionAction text. It is used by [CreateKotlinCallableAction].
 */
internal
object CreateKotlinCallableActionTextBuilder {
    fun build(callableKindAsString: String, request: CreateMethodFromKotlinUsageRequest): String {
        return buildString {
            append(KotlinBundle.message("text.create"))
            append(' ')
            append(descriptionOfCallableAsString(request))
            if (!endsWith(' ')) {
                append(' ')
            }
            append(callableKindAsString)

            if (request.methodName.isNotEmpty()) {
                val (receiver,_) = renderReceiver(request, request.call)
                append(" '$receiver${request.methodName}'")
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun descriptionOfCallableAsString(request: CreateMethodFromKotlinUsageRequest): String = when {
        request.isAbstractClassOrInterface -> KotlinBundle.message("text.abstract")
        request.isExtension -> KotlinBundle.message("text.extension")
        request.receiverExpression != null || request.receiverTypePointer != null -> KotlinBundle.message("text.member")
        else -> ""
    }

    // text, receiverTypeText
    @OptIn(KaExperimentalApi::class)
    fun renderReceiver(request: CreateMethodFromKotlinUsageRequest, container: KtElement): Pair<String, String> {
        analyze(request.call) {
            val receiverSymbol: KaSymbol?
            val receiverTypeText: String

            val renderer = if (request.isExtension) RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT else RAW_RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT
            val receiverType = request.receiverTypePointer?.restore()
            if (request.receiverExpression == null || request.receiverExpression.expressionType is KaErrorType) {
                if (receiverType == null) return "" to ""
                receiverSymbol = null
                receiverTypeText = cleanupRenderedType(
                    contextElement = request.call,
                    renderedType = receiverType.render(renderer, Variance.INVARIANT),
                )
            } else {
                receiverSymbol = request.receiverExpression.resolveExpression()
                val receiverType = receiverType ?: request.receiverExpression.expressionType
                val recPackageFqName = request.receiverExpression.expressionType?.convertToClass()?.classIdIfNonLocal?.packageFqName
                val addedPackage = if (recPackageFqName == container.containingKtFile.packageFqName || recPackageFqName == null || recPackageFqName.asString().startsWith("kotlin")) "" else recPackageFqName.asString()+"."
                // Since receiverExpression.getKtType() returns `kotlin/Unit` for a companion object, we first try the symbol resolution and its type rendering.
                val renderedReceiver = receiverSymbol?.renderAsReceiver(request.isAbstractClassOrInterface, receiverType, renderer)
                    ?: receiverType?.render(renderer, Variance.IN_VARIANCE)
                    ?: request.receiverExpression.text
                receiverTypeText = addedPackage + cleanupRenderedType(
                    contextElement = request.call,
                    renderedType = renderedReceiver,
                )
            }
            return if (request.isExtension && receiverSymbol is KaCallableSymbol) {
                val receiverType = receiverSymbol.returnType
                (if (receiverType is KaFunctionType) "($receiverTypeText)." else "$receiverTypeText.") to receiverTypeText
            } else {
                (receiverTypeText + if (request.isForCompanion) ".Companion." else ".") to receiverTypeText
            }
        }
    }

    context (KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KaSymbol.renderAsReceiver(isAbstract: Boolean, ktType: KaType?, renderer: KaTypeRenderer): String? {
        return when (this) {
            is KaCallableSymbol -> ktType?.selfOrSuperTypeWithAbstractMatch(isAbstract)
                ?.render(renderer, Variance.IN_VARIANCE)

            is KaClassLikeSymbol -> classId?.shortClassName?.asString() ?: render(KaDeclarationRendererForSource.WITH_SHORT_NAMES)
            else -> null
        }
    }

    context (KaSession)
    private fun KaType.selfOrSuperTypeWithAbstractMatch(isAbstract: Boolean): KaType? {
        if (this.hasAbstractDeclaration() == isAbstract || this is KaClassType && (symbol as? KaClassSymbol)?.classKind == KaClassKind.INTERFACE) return this
        return directSupertypes.firstNotNullOfOrNull { it.selfOrSuperTypeWithAbstractMatch(isAbstract) }
    }

    // We must use short names of types for create-from-usage quick-fix (or IntentionAction) text.
    @KaExperimentalApi
    private val RAW_RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT: KaTypeRenderer = KaTypeRendererForSource.WITH_SHORT_NAMES.with {
        classIdRenderer = createClassIdRenderer(true)
    }

    @KaExperimentalApi
    private val RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT: KaTypeRenderer = KaTypeRendererForSource.WITH_SHORT_NAMES.with {
        classIdRenderer = createClassIdRenderer(false)
    }

    @OptIn(KaExperimentalApi::class)
    private fun createClassIdRenderer(asRaw: Boolean): KaClassTypeQualifierRenderer = object : KaClassTypeQualifierRenderer {
        override fun renderClassTypeQualifier(
            analysisSession: KaSession,
            type: KaType,
            qualifiers: List<KaClassTypeQualifier>,
            typeRenderer: KaTypeRenderer,
            printer: PrettyPrinter
        ) {
            printer.append(qualifiers.joinToString(separator = ".") { it.name.asString() })
            if (!asRaw && type is KaClassType) {
                printer.printCollectionIfNotEmpty(type.typeArguments, prefix = "<", postfix = ">", separator = ", ", renderItem = {
                    typeRenderer.typeProjectionRenderer.renderTypeProjection(analysisSession, it, typeRenderer, this)
                })
            }
        }
    }


    context (KaSession)
    fun renderCandidatesOfReturnType(request: CreateMethodRequest, container: KtElement): List<String> {
        return request.returnType.mapNotNull { returnType ->
            renderTypeName(returnType, container)
        }
    }

    context (KaSession)
    @OptIn(KaExperimentalApi::class)
    fun renderTypeName(expectedType: ExpectedType, container: KtElement): String? {
        val ktType = if (expectedType is ExpectedKotlinType) expectedType.kaType else expectedType.toKtTypeWithNullability(container)
        if (ktType == null || ktType.isUnitType) return null
        return ktType.render(renderer = K2CreateFunctionFromUsageUtil.WITH_TYPE_NAMES_FOR_CREATE_ELEMENTS, position = Variance.INVARIANT)
    }

    context (KaSession)
    fun renderCandidatesOfParameterTypes(expectedParameters: List<ExpectedParameter>, container: KtElement?): List<ParamCandidate> {
        val generator = UniqueNameGenerator()
        return expectedParameters.map { expectedParameter ->
            val types = if (container == null) listOf("Any")
            else expectedParameter.expectedTypes.map {
                renderTypeName(it, container) ?: "Any"
            }
            ParamCandidate(expectedParameter.semanticNames.map { generator.generateUniqueName(it) }, types)
        }
    }
}