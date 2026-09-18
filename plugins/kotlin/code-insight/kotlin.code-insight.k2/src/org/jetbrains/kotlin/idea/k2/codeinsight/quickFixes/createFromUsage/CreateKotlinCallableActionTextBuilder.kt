// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.util.text.UniqueNameGenerator
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaPieceRenderer
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingContext
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOutput
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute
import org.jetbrains.kotlin.analysis.api.rendering.append
import org.jetbrains.kotlin.analysis.api.rendering.pushEmpty
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.rendering.render
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.directSupertypes
import org.jetbrains.kotlin.analysis.api.types.restore
import org.jetbrains.kotlin.idea.base.analysis.api.utils.approximateAnonymousObjectToSupertypeOrSelf
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction.ParamCandidate
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.convertToClass
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.hasAbstractDeclaration
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.toKtTypeWithNullability
import org.jetbrains.kotlin.idea.util.renderToStringIfPresent
import org.jetbrains.kotlin.idea.util.withShortNames
import org.jetbrains.kotlin.idea.util.withTypeApproximation
import org.jetbrains.kotlin.name.render
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

    private fun descriptionOfCallableAsString(request: CreateMethodFromKotlinUsageRequest): String = when {
        request.isAbstractClassOrInterface -> KotlinBundle.message("text.abstract")
        request.isExtension -> KotlinBundle.message("text.extension")
        request.receiverExpression != null || request.receiverTypePointer != null -> KotlinBundle.message("text.member")
        else -> ""
    }

    // text, receiverTypeText
    fun renderReceiver(request: CreateMethodFromKotlinUsageRequest, container: KtElement): Pair<String, String> {
        analyze(request.call) {
            val receiverSymbol: KaSymbol?
            val receiverTypeText: String

            val renderer = if (request.isExtension) RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT else RAW_RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT
            val receiverType = request.receiverTypePointer?.restore()
            if (request.receiverExpression == null || request.receiverExpression.expressionType is KaErrorType) {
                if (receiverType == null) return "" to ""
                receiverSymbol = null
                receiverTypeText = renderer.renderToString(receiverType)
            } else {
                receiverSymbol = request.receiverExpression.resolveExpression()
                val receiverType = receiverType ?: request.receiverExpression.expressionType
                val recPackageFqName = request.receiverExpression.expressionType?.convertToClass()?.classIdIfNonLocal?.packageFqName
                val addedPackage = if (recPackageFqName == container.containingKtFile.packageFqName || recPackageFqName == null || recPackageFqName.asString().startsWith("kotlin")) "" else recPackageFqName.asString()+"."
                // Since receiverExpression.getKtType() returns `kotlin/Unit` for a companion object, we first try the symbol resolution and its type rendering.
                val renderedReceiver = receiverSymbol?.renderAsReceiver(request, receiverType, renderer)
                    ?: renderer.copy { withTypeApproximation(Variance.IN_VARIANCE) }.renderToStringIfPresent(receiverType)
                    ?: request.receiverExpression.text
                receiverTypeText = buildString {
                    append(addedPackage)
                    val receiverIsCompanion = (receiverSymbol as? KaClassSymbol)?.classKind == KaClassKind.COMPANION_OBJECT
                    if (request.isForCompanion && receiverIsCompanion) {
                        (receiverSymbol.containingDeclaration as? KaClassSymbol)?.classId?.shortClassName?.let {
                            append(it.render())
                            append('.')
                        }
                    }
                    append(renderedReceiver)
                    if (request.isForCompanion && !receiverIsCompanion) {
                        append(".Companion")
                    }
                }
            }
            return if (receiverTypeText.isEmpty()) {
                "" to ""
            } else if (request.isExtension && receiverSymbol is KaCallableSymbol) {
                val receiverType = receiverSymbol.returnType
                (if (receiverType is KaFunctionType) "($receiverTypeText)." else "$receiverTypeText.") to receiverTypeText
            } else {
                "$receiverTypeText." to receiverTypeText
            }
        }
    }

    context(_: KaSession)
    private fun KaSymbol.renderAsReceiver(request: CreateMethodFromKotlinUsageRequest, ktType: KaType?, renderer: KaRenderer): String? {
        val isAbstract = request.isAbstractClassOrInterface
        // A receiver is an input position, so its type is approximated to its closest denotable subtype.
        val receiverRenderer = renderer.copy { withTypeApproximation(Variance.IN_VARIANCE) }
        return when (this) {
            is KaCallableSymbol -> ktType?.selfOrSuperTypeWithAbstractMatch(isAbstract)
                ?.let { receiverRenderer.renderToString(it) }

            is KaClassSymbol if (classKind == KaClassKind.ANONYMOUS_OBJECT) -> {
                when {
                    isAbstract -> ktType?.selfOrSuperTypeWithAbstractMatch(isAbstract = true)
                        ?.let { receiverRenderer.renderToString(it) }

                    request.isExtension -> ktType?.approximateAnonymousObjectToSupertypeOrSelf()
                        ?.let { receiverRenderer.renderToString(it) }

                    else -> ""
                }
            }

            is KaClassLikeSymbol -> classId?.shortClassName?.render() ?: CLASSIFIER_RENDERER_FOR_CREATE_FROM_USAGE_TEXT.renderToString(this)
            else -> null
        }
    }

    context(_: KaSession)
    private fun KaType.selfOrSuperTypeWithAbstractMatch(isAbstract: Boolean): KaType? {
        if (this.hasAbstractDeclaration() == isAbstract || this is KaClassType && (symbol as? KaClassSymbol)?.classKind == KaClassKind.INTERFACE) return this
        return directSupertypes.firstNotNullOfOrNull { it.selfOrSuperTypeWithAbstractMatch(isAbstract) }
    }

    /** Renders a local or anonymous classifier, which has no name to put into the action text, by its header alone. */
    private val CLASSIFIER_RENDERER_FOR_CREATE_FROM_USAGE_TEXT: KaRenderer = KaRenderer.withShortNames.copy {
        pushEmpty(KaPiece.ClassBody)
    }

    // We must use short names of types for create-from-usage quick-fix (or IntentionAction) text.
    private val RAW_RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT: KaRenderer = KaRenderer.withShortNames.copy {
        push(ClassTypeForActionTextRenderer(asRaw = true))
    }

    private val RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT: KaRenderer = KaRenderer.withShortNames.copy {
        push(ClassTypeForActionTextRenderer(asRaw = false))
    }


    context(_: KaSession)
    fun renderCandidatesOfReturnType(request: CreateMethodRequest, container: KtElement): List<String> {
        return request.returnType.mapNotNull { returnType ->
            renderTypeName(returnType, container)
        }
    }

    context(_: KaSession)
    fun renderTypeName(expectedType: ExpectedType, container: KtElement): String? {
        val ktType = if (expectedType is ExpectedKotlinType) expectedType.kaType else expectedType.toKtTypeWithNullability(container)
        if (ktType == null || ktType.classId == KaStandardTypeClassIds.UNIT) return null
        return K2CreateFunctionFromUsageUtil.WITH_TYPE_NAMES_FOR_CREATE_ELEMENTS.renderToString(ktType)
    }

    context(_: KaSession)
    fun renderCandidatesOfParameterTypes(expectedParameters: List<ExpectedParameter>, container: KtElement?): List<ParamCandidate> {
        val generator = UniqueNameGenerator()
        return expectedParameters.mapNotNull { expectedParameter ->
            val types = if (container == null) {
                listOf("Any")
            } else {
                expectedParameter.expectedTypes.map {
                    renderTypeName(it, container) ?: "Any"
                }
            }
            val semanticNames = expectedParameter.semanticNames.ifEmpty { return@mapNotNull null }
            ParamCandidate(semanticNames.map { generator.generateUniqueName(it) }, types)
        }
    }
}

/**
 * Renders a class type for quick-fix presentation text: the visible qualifiers only, without the ones which are implementation details of a
 * generated wrapper class. With [asRaw] the type arguments are dropped as well.
 */
private class ClassTypeForActionTextRenderer(private val asRaw: Boolean) : KaPieceRenderer<KaClassType>(KaPiece.ClassType) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaClassType, next: () -> Unit): Boolean {
        val visibleQualifiers = K2CreateFunctionFromUsageUtil.filterOutImplementationDetailQualifiers(value, value.qualifiers)
        output.append(visibleQualifiers.joinToString(separator = ".") { it.name.asString() }, KaTextAttribute.Identifier)

        if (!asRaw && value.typeArguments.isNotEmpty()) {
            render(value.typeArguments, KaPiece.TypeArgumentList)
        }

        render(value, KaPiece.TypeNullability)
        return true
    }
}
