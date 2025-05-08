// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.*
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.classifiers.KaSingleTypeParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.NOT_NULL_ANNOTATIONS
import org.jetbrains.kotlin.load.java.NULLABLE_ANNOTATIONS
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference
import org.jetbrains.kotlin.types.Variance
import java.util.*

internal object ChangeMemberFunctionSignatureFixFactory {
    val nothingToOverrideFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NothingToOverride ->
        val function = diagnostic.psi as? KtNamedFunction ?: return@ModCommandBased emptyList()
        val signatures = computePossibleSignatures(function)
        if (signatures.isEmpty()) return@ModCommandBased emptyList()
        val signature = signatures.singleOrNull()
        if (signature != null) {
            return@ModCommandBased listOf(ChangeMemberFunctionSignatureFix(function, signature, KotlinBundle.message("fix.change.signature.function.text", signature.preview)))
        }
        return@ModCommandBased listOf(ChooseSuperSignatureFix(function, signatures))
    }

    private class ChooseSuperSignatureFix(function: KtNamedFunction, signatures: List<Signature>): ModCommandAction {
        val fixes = signatures.map { ChangeMemberFunctionSignatureFix(function, it, it.preview) }

        override fun getPresentation(context: ActionContext): Presentation =
            Presentation.of(KotlinBundle.message("fix.change.signature.function.text.generic"))

        override fun perform(context: ActionContext): ModCommand =
            ModChooseAction(KotlinBundle.message("fix.change.signature.function.text.generic"), fixes)

        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.change.signature.function.family")
    }

    /**
     * Computes all the signatures a 'functionElement' could be changed to in order to remove NOTHING_TO_OVERRIDE error.
     */
    private fun KaSession.computePossibleSignatures(functionElement: KtNamedFunction): List<Signature> {
        if (functionElement.valueParameterList == null) { // we won't be able to modify its signature
            return emptyList()
        }

        val functionSymbol = functionElement.symbol
        val superFunctions = getPossibleSuperFunctions(functionSymbol)

        return superFunctions.asSequence().map { signatureToMatch(functionSymbol, it) }.distinctBy { it.sourceCode }.toList()
    }

    private class Signature(@NlsSafe val preview: String, val sourceCode: String)


    /**
     * Changes function's signature to match superFunction's signature. Returns new descriptor.
     */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.signatureToMatch(function: KaFunctionSymbol, superFunction: KaNamedFunctionSymbol): Signature {
        val superParameters = superFunction.valueParameters
        val parameters = function.valueParameters
        val subClass = function.containingSymbol
        val superClass = superFunction.containingSymbol
        val substitutor = if (subClass is KaClassSymbol && superClass is KaClassSymbol) createInheritanceTypeSubstitutor(subClass, superClass) else null

        val names = superParameters.map { it.name.asString() }.toMutableList()
        val substitutedTypes = superParameters.map { superParam ->
            val returnType = superParam.returnType
            (substitutor?.substitute(returnType) ?: returnType).approximateToSubPublicDenotableOrSelf(false)
        }.toMutableList()

        // Parameters in superFunction, which are matched in new function signature:
        val matched = BitSet(superParameters.size) // Parameters in this function, which are used in new function signature:
        val used = BitSet(superParameters.size)

        matchParameters(ParameterChooser.MatchNames, superParameters, parameters, substitutedTypes, names, matched, used)
        matchParameters(ParameterChooser.MatchTypes, superParameters, parameters, substitutedTypes, names, matched, used)

        val preview = getSignature(substitutedTypes, names, superFunction, KaDeclarationRendererForSource.WITH_SHORT_NAMES)
        val sourceCode = getSignature(substitutedTypes, names, superFunction, KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES)

        return Signature(preview, sourceCode)
    }

    private interface ParameterChooser {
        context(KaSession)
        fun accept(parameter: KaValueParameterSymbol, superParameter: KaValueParameterSymbol, newType: KaType): Boolean

        object MatchNames : ParameterChooser {
            context(KaSession)
            override fun accept(parameter: KaValueParameterSymbol, superParameter: KaValueParameterSymbol, newType: KaType): Boolean {
                return parameter.name == superParameter.name
            }
        }

        object MatchTypes : ParameterChooser {
            context(KaSession)
            override fun accept(parameter: KaValueParameterSymbol, superParameter: KaValueParameterSymbol, newType: KaType): Boolean {
                return parameter.returnType.semanticallyEquals(newType)
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.getSignature(
        types: List<KaType>,
        names: List<String>,
        superFunction: KaNamedFunctionSymbol,
        declarationRenderer: KaDeclarationRenderer
    ): String {
        return buildString {
            if (superFunction.isSuspend) {
                append("suspend ")
            }
            if (superFunction.isOperator) {
                append("operator ")
            }
            if (superFunction.isInfix) {
                append("infix ")
            }
            if (superFunction.isExternal) {
                append("external ")
            }
            if (superFunction.isInline) {
                append("inline ")
            }
            if (superFunction.isTailRec) {
                append("tailrec ")
            }

            append("fun ")
            if (superFunction.typeParameters.isNotEmpty()) {
                append(superFunction.typeParameters.joinToString(prefix = "<", postfix = "> ") {
                    it.render(declarationRenderer.with {
                        singleTypeParameterRenderer = KaSingleTypeParameterSymbolRenderer.WITH_COMMA_SEPARATED_BOUNDS
                    })
                })
            }
            superFunction.receiverType?.let {
                val needBraces = it is KaFunctionType || it is KaDefinitelyNotNullType
                if (needBraces) append("(")
                append(it.render(declarationRenderer.typeRenderer, Variance.INVARIANT))
                if (needBraces) append(")")
                append(".")
            }


            append(superFunction.name.asString())
            append(names.zip(types).joinToString(prefix = "(", postfix = ")") { (name, type) ->
               name + ": " + type.render(declarationRenderer.typeRenderer, Variance.INVARIANT)
            })
            superFunction.returnType.takeUnless { it.isUnitType }?.let {
                append(": ")
                append(it.render(declarationRenderer.typeRenderer, Variance.INVARIANT))
            }
        }
    }

    private fun KaSession.matchParameters(
        parameterChooser: ParameterChooser,
        superParameters: List<KaValueParameterSymbol>,
        parameters: List<KaValueParameterSymbol>,
        substitutedTypes: MutableList<KaType>,
        names: MutableList<String>,
        matched: BitSet,
        used: BitSet
    ) {
        superParameters.forEachIndexed { index, superParameter ->
            if (!matched[index]) {
                val substitutedType = substitutedTypes[index]
                parameters.forEachIndexed { index1, parameter ->
                    if (parameterChooser.accept(parameter, superParameter, substitutedType) && !used[index1]) {
                        used[index1] = true
                        matched[index] = true
                        names[index] = parameter.name.asString()
                        return@forEachIndexed
                    }
                }
            }
        }
    }

    private fun KaSession.getPossibleSuperFunctions(functionSymbol: KaFunctionSymbol): List<KaNamedFunctionSymbol> {
        val containingClass = functionSymbol.containingSymbol as? KaClassSymbol ?: return emptyList()

        val name = functionSymbol.name ?: return emptyList()
        return containingClass.superTypes.flatMap { superType ->
            (superType.symbol as? KaClassSymbol)?.memberScope?.callables(name)?.filterIsInstance<KaNamedFunctionSymbol>() ?: emptySequence()
        }.filter {
            it.origin != KaSymbolOrigin.INTERSECTION_OVERRIDE && it.origin != KaSymbolOrigin.SUBSTITUTION_OVERRIDE && it.modality != KaSymbolModality.FINAL && it.visibility != KaSymbolVisibility.PRIVATE
        }
    }

    private class ChangeMemberFunctionSignatureFix(function: KtNamedFunction, signature: Signature, @Nls val text: String) :
        KotlinPsiUpdateModCommandAction.ElementBased<KtNamedFunction, Signature>(function, signature) {

        override fun getPresentation(
            context: ActionContext, element: KtNamedFunction
        ): Presentation {
            return Presentation.of(text)
        }

        override fun getFamilyName(): String = KotlinBundle.message("fix.change.signature.function.family")

        override fun invoke(
            actionContext: ActionContext, element: KtNamedFunction, elementContext: Signature, updater: ModPsiUpdater
        ) {
            changeSignature(element, elementContext)
        }

        private fun changeSignature(function: KtNamedFunction, signature: Signature) {

            val patternFunction = KtPsiFactory(function.project).createFunction(signature.sourceCode)

            if (patternFunction.hasModifier(KtTokens.SUSPEND_KEYWORD)) {
                function.addModifier(KtTokens.SUSPEND_KEYWORD)
            }

            val newTypeRef = function.setTypeReference(patternFunction.typeReference)
            if (newTypeRef != null) {
                shortenReferences(newTypeRef)
            }

            patternFunction.valueParameters.forEach { param ->
                param.annotationEntries.forEach { a ->
                    a.typeReference?.run {
                        val fqName = FqName(text)
                        if (fqName in (NULLABLE_ANNOTATIONS + NOT_NULL_ANNOTATIONS)) a.delete()
                    }
                }
            }

            val newParameterList = patternFunction.valueParameterList?.let {
                function.valueParameterList?.replace(it)
            } as KtParameterList
            shortenReferences(newParameterList)

            val patternFunctionReceiver = patternFunction.receiverTypeReference
            if (patternFunctionReceiver == null) {
                if (function.receiverTypeReference != null) {
                    function.setReceiverTypeReference(null)
                }
            } else {
                function.setReceiverTypeReference(patternFunction.receiverTypeReference)?.let {
                    shortenReferences(it)
                }
            }

            val patternTypeParameterList = patternFunction.typeParameterList
            if (patternTypeParameterList != null) {
                shortenReferences(
                    (if (function.typeParameterList != null) function.typeParameterList?.replace(patternTypeParameterList)
                    else function.addAfter(patternTypeParameterList, function.funKeyword)) as KtTypeParameterList
                )
            } else function.typeParameterList?.delete()

        }
    }
}