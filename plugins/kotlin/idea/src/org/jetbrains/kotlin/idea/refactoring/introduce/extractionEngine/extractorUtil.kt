// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiUnificationResult
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiUnificationResult.StrictSuccess
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiUnificationResult.WeakSuccess
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.core.toVisibility
import org.jetbrains.kotlin.idea.inspections.PublicApiImplicitTypeInspection
import org.jetbrains.kotlin.idea.inspections.UseExpressionBodyInspection
import org.jetbrains.kotlin.idea.intentions.RemoveExplicitTypeArgumentsIntention
import org.jetbrains.kotlin.idea.refactoring.introduce.*
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.*
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.getAllAccessibleVariables
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.psi.patternMatching.KotlinPsiUnifier
import org.jetbrains.kotlin.idea.util.psi.patternMatching.UnifierParameter
import org.jetbrains.kotlin.idea.util.psi.patternMatching.match
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isFlexible
import java.util.*

fun ExtractionGeneratorConfiguration.getSignaturePreview() = Generator.getSignaturePreview(this)

fun KotlinType.isSpecial(): Boolean {
    val classDescriptor = this.constructor.declarationDescriptor as? ClassDescriptor ?: return false
    return classDescriptor.name.isSpecial || DescriptorUtils.isLocal(classDescriptor)
}

fun createNameCounterpartMap(from: KtElement, to: KtElement): Map<KtSimpleNameExpression, KtSimpleNameExpression> {
    return from.collectDescendantsOfType<KtSimpleNameExpression>().zip(to.collectDescendantsOfType<KtSimpleNameExpression>()).toMap()
}



fun ExtractableCodeDescriptor.findDuplicates(): List<DuplicateInfo<KotlinType>> {
    fun processWeakMatch(match: WeakSuccess<*>, newControlFlow: ControlFlow<KotlinType>): Boolean {
        val valueCount = controlFlow.outputValues.size

        val weakMatches = HashMap(match.weakMatches)
        val currentValuesToNew = HashMap<OutputValue<KotlinType>, OutputValue<KotlinType>>()

        fun matchValues(currentValue: OutputValue<KotlinType>, newValue: OutputValue<KotlinType>): Boolean {
            if ((currentValue is Jump) != (newValue is Jump)) return false
            if (currentValue.originalExpressions.zip(newValue.originalExpressions).all { weakMatches[it.first] == it.second }) {
                currentValuesToNew[currentValue] = newValue
                weakMatches.keys.removeAll(currentValue.originalExpressions)
                return true
            }
            return false
        }

        if (valueCount == 1) {
            matchValues(controlFlow.outputValues.first(), newControlFlow.outputValues.first())
        } else {
            outer@
            for (currentValue in controlFlow.outputValues)
                for (newValue in newControlFlow.outputValues) {
                    if ((currentValue is ExpressionValue) != (newValue is ExpressionValue)) continue
                    if (matchValues(currentValue, newValue)) continue@outer
                }
        }

        return currentValuesToNew.size == valueCount && weakMatches.isEmpty()
    }

    fun getControlFlowIfMatched(match: KotlinPsiUnificationResult.Success<*>): ControlFlow<KotlinType>? {
        val analysisResult = extractionData.copy(originalRange = match.range).performAnalysis()
        if (analysisResult.status != AnalysisResult.Status.SUCCESS) return null

        val newControlFlow = analysisResult.descriptor!!.controlFlow
        if (newControlFlow.outputValues.isEmpty()) return newControlFlow
        if (controlFlow.outputValues.size != newControlFlow.outputValues.size) return null

        val matched = when (match) {
            is StrictSuccess -> true
            is WeakSuccess -> processWeakMatch(match, newControlFlow)
            else -> throw AssertionError("Unexpected unification result: $match")
        }

        return if (matched) newControlFlow else null
    }

    val unifierParameters = parameters.map { UnifierParameter(it.originalDescriptor, it.parameterType) }

    val unifier = KotlinPsiUnifier(unifierParameters, true)

    val scopeElement = getOccurrenceContainer() ?: return Collections.emptyList()
    val originalTextRange = extractionData.originalRange.getPhysicalTextRange()
    return extractionData
        .originalRange
        .match(scopeElement, unifier)
        .asSequence()
        .filter { !(it.range.getPhysicalTextRange().intersects(originalTextRange)) }
        .mapNotNull { match ->
            val controlFlow = getControlFlowIfMatched(match)
            val range = with(match.range) {
                (elements.singleOrNull() as? KtStringTemplateEntryWithExpression)?.expression?.toRange() ?: this
            }

            controlFlow?.let {
                DuplicateInfo(range, it, unifierParameters.map { param ->
                    match.substitution.getValue(param).text!!
                })
            }
        }
        .toList()
}

private object Generator: ExtractFunctionGenerator<KotlinType, ExtractionResult>() {
    override val nameGenerator: IExtractionNameSuggester<KotlinType> = ExtractNameSuggester

    override fun createTypeDescriptor(data: IExtractionData): TypeDescriptor<KotlinType> {
        return KotlinTypeDescriptor(data as ExtractionData)
    }

    override fun IExtractionGeneratorConfiguration<KotlinType>.collapseBody(body: KtBlockExpression) {
        val bodyExpression = body.statements.singleOrNull()
        val bodyOwner = body.parent as KtDeclarationWithBody
        val useExpressionBodyInspection = UseExpressionBodyInspection()
        if (bodyExpression != null && useExpressionBodyInspection.isActiveFor(bodyOwner)) {
            useExpressionBodyInspection.simplify(bodyOwner, !useExplicitReturnType(this))
        }
    }

    private fun getPublicApiInspectionIfEnabled(descriptor: IExtractableCodeDescriptor<KotlinType>): PublicApiImplicitTypeInspection? {
        val project = descriptor.extractionData.project
        val inspectionProfileManager = ProjectInspectionProfileManager.getInstance(project)
        val inspectionProfile = inspectionProfileManager.currentProfile
        val state = inspectionProfile.getToolsOrNull("PublicApiImplicitType", project)?.defaultState ?: return null
        if (!state.isEnabled || state.level == HighlightDisplayLevel.DO_NOT_SHOW) return null
        return state.tool.tool as? PublicApiImplicitTypeInspection
    }

    private fun useExplicitReturnType(config: IExtractionGeneratorConfiguration<KotlinType>): Boolean {
        val descriptor = config.descriptor
        if (descriptor.returnType.isFlexible()) return true
        val inspection = getPublicApiInspectionIfEnabled(descriptor) ?: return false
        val targetClass = (descriptor.extractionData.targetSibling.parent as? KtClassBody)?.parent as? KtClassOrObject
        if ((targetClass != null && targetClass.isLocal) || descriptor.extractionData.isLocal()) return false
        val visibility = (descriptor.visibility ?: KtTokens.DEFAULT_VISIBILITY_KEYWORD).toVisibility()
        return when {
            visibility.isPublicAPI -> true
            inspection.reportInternal && visibility == DescriptorVisibilities.INTERNAL -> true
            inspection.reportPrivate && visibility == DescriptorVisibilities.PRIVATE -> true
            else -> false
        }
    }

    override fun checkTypeArgumentsAreRedundant(args: KtTypeArgumentList): Boolean {
        return RemoveExplicitTypeArgumentsIntention.isApplicableTo(args, false)
    }

    override fun resolveNameConflict(property: KtProperty) {
        if ((property.descriptor as? PropertyDescriptor)?.let { DescriptorUtils.isOverride(it) } == true) {
            val scope = property.getResolutionScope()
            val newName = KotlinNameSuggester.suggestNameByName(property.name!!) {
                it != property.name && scope.getAllAccessibleVariables(Name.identifier(it)).isEmpty()
            }
            property.setName(newName)
        }
    }

    override fun IExtractionGeneratorConfiguration<KotlinType>.createExtractionResult(
        declaration: KtNamedDeclaration,
        duplicatesReplacer: Map<KotlinPsiRange, () -> Unit>
    ): ExtractionResult {
        return ExtractionResult(this as ExtractionGeneratorConfiguration, declaration, duplicatesReplacer)
    }
}

fun ExtractionGeneratorConfiguration.generateDeclaration(
    declarationToReplace: KtNamedDeclaration? = null
): ExtractionResult {
    return Generator.generateDeclaration(this, declarationToReplace)
}