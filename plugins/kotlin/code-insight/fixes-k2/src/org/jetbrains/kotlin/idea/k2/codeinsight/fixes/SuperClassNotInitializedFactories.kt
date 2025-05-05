// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.SupertypeNotInitialized
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.defaultValue
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.match

internal object SuperClassNotInitializedFactories {

    val changeToConstructorCall: KotlinQuickFixFactory.ModCommandBased<SupertypeNotInitialized> =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: SupertypeNotInitialized ->
            val typeReference = diagnostic.psi as? KtTypeReference
                ?: return@ModCommandBased emptyList()
            val superTypeEntry = typeReference.parent as? KtSuperTypeEntry
                ?: return@ModCommandBased emptyList()
            val superClassSymbol = typeReference.type.expandedSymbol as? KaNamedClassSymbol
                ?: return@ModCommandBased emptyList()

            if (!superClassSymbol.isInheritableWithSuperConstructorCall(superTypeEntry)) {
                return@ModCommandBased emptyList()
            }

            val constructors = superClassSymbol.declaredMemberScope.constructors
            buildList {
                add(AddParenthesisFix(superTypeEntry, moveCaretIntoParenthesis = constructors.any { it.valueParameters.isNotEmpty() }))
                addAll(createAddParametersFixes(superTypeEntry, superClassSymbol))
            }
        }

    context(KaSession)
    private fun KaNamedClassSymbol.isInheritableWithSuperConstructorCall(superTypeEntry: KtSuperTypeEntry): Boolean {
        if (classKind != KaClassKind.CLASS) return false
        return when (modality) {
            KaSymbolModality.FINAL -> false
            KaSymbolModality.OPEN -> true
            KaSymbolModality.ABSTRACT -> true
            KaSymbolModality.SEALED -> {
                val subClass = superTypeEntry.parentOfType<KtClassOrObject>()
                subClass?.isLocal == false
                        && classId?.packageFqName == superTypeEntry.containingKtFile.packageFqName
                        && containingModule == useSiteModule
            }
        }
    }

    private data class ElementContext(
        val moveCaretIntoParenthesis: Boolean,
    )

    private class AddParenthesisFix(
        element: KtSuperTypeEntry,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtSuperTypeEntry, ElementContext>(element, elementContext) {

        constructor(
            element: KtSuperTypeEntry,
            moveCaretIntoParenthesis: Boolean,
        ) : this(
            element,
            ElementContext(moveCaretIntoParenthesis),
        )

        override fun invoke(
            actionContext: ActionContext,
            element: KtSuperTypeEntry,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val withParenthesis = element.replaced(KtPsiFactory(actionContext.project).createSuperTypeCallEntry(element.text + "()"))
            if (elementContext.moveCaretIntoParenthesis) {
                withParenthesis.valueArgumentList?.leftParenthesis?.endOffset?.let { offset ->
                    updater.moveCaretTo(offset)
                }
            }
        }

        override fun getFamilyName(): String = KotlinBundle.message("change.to.constructor.invocation")
        override fun getPresentation(context: ActionContext, element: KtSuperTypeEntry): Presentation =
            Presentation.of(familyName).withPriority(PriorityAction.Priority.HIGH)
    }

    private class AddParametersContext(
        val parametersInfo: List<ParameterInfo>,
        val superClassName: String,
        val renderedTypesForName: String,
    )

    private class ParameterInfo(
        val renderedName: String,
        val parameterType: String,
        val parameterGenerationInfo: ParameterGenerationInfo,
        val varargInfo: VarargInfo,
    ) {
        val isVararg: Boolean get() = varargInfo != VarargInfo.NotVararg
    }

    private sealed class ParameterGenerationInfo {
        object Reused : ParameterGenerationInfo()
        data class New(val parameterText: String) : ParameterGenerationInfo()
    }

    private enum class VarargInfo {
        NotVararg, VarargNoSpread, VarargWithSpread;
    }

    @OptIn(KaExperimentalApi::class)
    private class AddParametersFix(
        element: KtSuperTypeEntry,
        context: AddParametersContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtSuperTypeEntry, AddParametersContext>(element, context) {
        private val renderedTypesForName: String = context.renderedTypesForName
        private val superClassName: String = context.superClassName

        override fun invoke(
            actionContext: ActionContext,
            element: KtSuperTypeEntry,
            elementContext: AddParametersContext,
            updater: ModPsiUpdater,
        ) {
            val containingClass = getContainingClass(element) ?: return
            val psiFactory = KtPsiFactory(actionContext.project)
            val constructorParameterList = containingClass.createPrimaryConstructorParameterListIfAbsent()

            elementContext.parametersInfo
                .map { it.parameterGenerationInfo }
                .filterIsInstance<ParameterGenerationInfo.New>()
                .map { newParamInfo -> psiFactory.createParameter(newParamInfo.parameterText) }
                .forEach { constructorParameterList.addParameter(it) }

            val delegatorCallArgumentsText = elementContext.parametersInfo.joinToString(separator = ", ") { info ->
                "*".takeIf { info.varargInfo == VarargInfo.VarargWithSpread }.orEmpty() + info.renderedName
            }
            val delegatorCall = psiFactory.createSuperTypeCallEntry("${element.text}($delegatorCallArgumentsText)")

            element.replace(delegatorCall)
            shortenReferences(constructorParameterList)
        }

        override fun getFamilyName(): @IntentionFamilyName String {
            return KotlinBundle.message("add.constructor.parameters.from.superclass")
        }

        override fun getPresentation(context: ActionContext, element: KtSuperTypeEntry): Presentation {
            return Presentation.of(
                KotlinBundle.message("add.constructor.parameters.from.0.1", superClassName, renderedTypesForName)
            )
        }
    }

    private fun getContainingClass(superTypeEntry: KtSuperTypeEntry): KtClass? {
        return superTypeEntry.parents.match(KtSuperTypeList::class, last = KtClass::class)
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createAddParametersFixes(superTypeEntry: KtSuperTypeEntry, superClassSymbol: KaNamedClassSymbol): List<AddParametersFix> {
        val containingClass = getContainingClass(superTypeEntry) ?: return emptyList()
        val containingClassSymbol = containingClass.classSymbol ?: return emptyList()
        val inheritanceSubstitutor = createInheritanceTypeSubstitutor(containingClassSymbol, superClassSymbol)
            ?: return emptyList()
        val substitutedSuperConstructors = superClassSymbol.memberScope.constructors.filter { constructorSymbol ->
            constructorSymbol.isVisible(containingClass) && constructorSymbol.valueParameters.isNotEmpty()
        }.map { it.substitute(inheritanceSubstitutor) }

        return substitutedSuperConstructors.mapNotNull {
            createSingleConstructorFix(superClassSymbol, superTypeEntry, containingClassSymbol, it)
        }.toList()
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createSingleConstructorFix(
        superClassSymbol: KaNamedClassSymbol,
        superTypeEntry: KtSuperTypeEntry,
        classDeclaration: KaClassSymbol,
        substitutedSuperConstructor: KaFunctionSignature<KaConstructorSymbol>,
    ): AddParametersFix? {
        val superConstructorParameters = substitutedSuperConstructor.valueParameters
        if (superConstructorParameters.isEmpty()) return null
        if (superConstructorParameters.any { it.returnType is KaErrorType }) return null
        val primaryConstructor = classDeclaration.memberScope.constructors.singleOrNull { it.isPrimary } ?: return null
        val primaryConstructorParameters = primaryConstructor.valueParameters

        val parametersInfo = mutableListOf<ParameterInfo>()

        for (superParameter in superConstructorParameters) {
            val parameterInfo = prepareParameterInfo(superParameter, primaryConstructorParameters) ?: return null
            parametersInfo.add(parameterInfo)
        }

        val superClassName = superClassSymbol.name.asString()
        val renderedTypesForName = parametersInfo.joinToString(separator = ", ", prefix = "(", postfix = ")") { parameterInfo ->
            // INVARIANT position to keep types without approximation — shown in the quick fix name to distinguish between constructors
            "vararg ".takeIf { parameterInfo.isVararg }.orEmpty() +
                    parameterInfo.parameterType
        }

        return AddParametersFix(
            superTypeEntry,
            AddParametersContext(parametersInfo, superClassName, renderedTypesForName)
        )
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun prepareParameterInfo(
        superParameter: KaVariableSignature<KaValueParameterSymbol>,
        primaryConstructorParameters: List<KaValueParameterSymbol>,
    ): ParameterInfo? {
        val renderedName = superParameter.name.render()
        val isVararg = superParameter.symbol.isVararg
        val existingParameter = primaryConstructorParameters.singleOrNull { it.name == superParameter.name }
        val superParameterType = superParameter.returnType
        if (existingParameter != null) {
            // use the existing constructor parameter if its type fits
            val existingParameterType = if (isVararg) {
                existingParameter.returnType.arrayElementType ?: existingParameter.returnType
            } else existingParameter.returnType
            if (existingParameterType.isSubtypeOf(superParameterType)) {
                val varargInfo = when {
                    !isVararg -> VarargInfo.NotVararg
                    existingParameter.returnType.arrayElementType != null -> VarargInfo.VarargWithSpread
                    // can pass an existing plain parameter as an argument for the super vararg parameter
                    else -> VarargInfo.VarargNoSpread
                }
                return ParameterInfo(
                    renderedName = renderedName,
                    parameterType = superParameterType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT),
                    parameterGenerationInfo = ParameterGenerationInfo.Reused,
                    varargInfo = varargInfo,
                )

            } else return null
        } else {
            val defaultValueText = superParameter.symbol.defaultValue?.text?.let { " = $it" } ?: ""
            // IN variance: the type is a parameter type, approximating safely to a denotable type
            val renderedType = superParameterType.render(position = Variance.IN_VARIANCE)
            val varargKeywordOrEmpty = "vararg ".takeIf { isVararg }.orEmpty()
            val parameterText = "$varargKeywordOrEmpty$renderedName: ${renderedType}$defaultValueText"
            return ParameterInfo(
                renderedName = renderedName,
                parameterType = superParameterType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT),
                parameterGenerationInfo = ParameterGenerationInfo.New(parameterText),
                varargInfo = if (isVararg) VarargInfo.VarargWithSpread else VarargInfo.NotVararg,
            )
        }
    }
}
