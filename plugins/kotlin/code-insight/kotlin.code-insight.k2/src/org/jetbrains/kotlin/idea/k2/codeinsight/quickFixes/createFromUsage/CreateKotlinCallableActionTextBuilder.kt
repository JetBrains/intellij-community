// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.KtTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtClassTypeQualifierRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtClassType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
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
        request.receiverExpression != null || request.receiverType != null -> KotlinBundle.message("text.member")
        else -> ""
    }

    // text, receiverTypeText
    fun renderReceiver(request: CreateMethodFromKotlinUsageRequest, container: KtElement): Pair<String,String> {
        analyze(request.call) {
            val receiverSymbol: KtSymbol?
            val receiverTypeText: String
            if (request.receiverExpression == null) {
                if (request.receiverType == null) return "" to ""
                receiverSymbol = null
                receiverTypeText = request.receiverType.render(RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT, Variance.INVARIANT)
            } else {
                receiverSymbol = request.receiverExpression.resolveExpression()
                val receiverType = request.receiverType ?: request.receiverExpression.getKtType()
                val recPackageFqName = request.receiverExpression.getKtType()?.convertToClass()?.classIdIfNonLocal?.packageFqName
                val addedPackage = if (recPackageFqName == container.containingKtFile.packageFqName || recPackageFqName == null || recPackageFqName.asString().startsWith("kotlin")) "" else recPackageFqName.asString()+"."
                // Since receiverExpression.getKtType() returns `kotlin/Unit` for a companion object, we first try the symbol resolution and its type rendering.
                val renderedReceiver = receiverSymbol?.renderAsReceiver(request.isAbstractClassOrInterface, receiverType)
                    ?: receiverType?.render(RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT, Variance.INVARIANT)
                    ?: request.receiverExpression.text
                receiverTypeText = addedPackage + renderedReceiver
            }
            return if (request.isExtension && receiverSymbol is KtCallableSymbol) {
                val receiverType = receiverSymbol.returnType
                (if (receiverType is KtFunctionalType) "($receiverTypeText)." else "$receiverTypeText.") to receiverTypeText
            } else {
                (receiverTypeText + if (receiverSymbol is KtClassLikeSymbol && !(receiverSymbol is KtClassOrObjectSymbol && receiverSymbol.classKind == KtClassKind.OBJECT)) ".Companion." else ".") to receiverTypeText
            }
        }
    }

    context (KtAnalysisSession)
    private fun KtSymbol.renderAsReceiver(isAbstract: Boolean, ktType: KtType?): String? {
        return when (this) {
            is KtCallableSymbol -> ktType?.selfOrSuperTypeWithAbstractMatch(isAbstract)
                ?.render(RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT, Variance.INVARIANT)

            is KtClassLikeSymbol -> classIdIfNonLocal?.shortClassName?.asString() ?: render(KtDeclarationRendererForSource.WITH_SHORT_NAMES)
            else -> null
        }
    }

    context (KtAnalysisSession)
    private fun KtType.selfOrSuperTypeWithAbstractMatch(isAbstract: Boolean): KtType? {
        if (this.hasAbstractDeclaration() == isAbstract || this is KtNonErrorClassType && (classSymbol as? KtClassOrObjectSymbol)?.classKind == KtClassKind.INTERFACE) return this
        return getDirectSuperTypes().firstNotNullOfOrNull { it.selfOrSuperTypeWithAbstractMatch(isAbstract) }
    }

    // We must use short names of types for create-from-usage quick-fix (or IntentionAction) text.
    private val RENDERER_OPTION_FOR_CREATE_FROM_USAGE_TEXT: KtTypeRenderer = KtTypeRendererForSource.WITH_SHORT_NAMES.with {
        classIdRenderer = object : KtClassTypeQualifierRenderer {
            context(KtAnalysisSession, KtTypeRenderer)
            override fun renderClassTypeQualifier(type: KtClassType, printer: PrettyPrinter) {
                printer.append(type.qualifiers.joinToString(separator = ".") { it.name.asString() })
            }
        }
    }
}