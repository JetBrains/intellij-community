// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.*
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.approximateFlexibleTypes
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.types.typeUtil.nullability
import org.jetbrains.kotlin.utils.IDEAPlatforms
import org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI

interface Parameter: IParameter<KotlinType> {
    val originalDescriptor: DeclarationDescriptor
}

val ControlFlow<KotlinType>.possibleReturnTypes: List<KotlinType>
    get() {
        val returnType = outputValueBoxer.returnType
        return when {
            !returnType.isNullabilityFlexible() ->
                listOf(returnType)
            returnType.nullability() != TypeNullability.FLEXIBLE ->
                listOf(returnType.approximateFlexibleTypes())
            else ->
                (returnType.unwrap() as FlexibleType).let { listOf(it.upperBound, it.lowerBound) }
        }
    }


fun ControlFlow<KotlinType>.copy(oldToNewParameters: Map<Parameter, Parameter>): ControlFlow<KotlinType> {
    val newOutputValues = outputValues.map {
        if (it is ParameterUpdate) ParameterUpdate(oldToNewParameters[it.parameter]!!, it.originalExpressions) else it
    }
    return copy(outputValues = newOutputValues)
}


class WrapObjectInWithReplacement(val descriptor: ClassDescriptor) : WrapInWithReplacement<KotlinType>() {
    override val argumentText: String
        get() = IdeDescriptorRenderers.SOURCE_CODE.renderClassifierName(descriptor)
}

data class ExtractableCodeDescriptor(
    override val extractionData: ExtractionData,
    val originalContext: BindingContext,
    override val suggestedNames: List<String>,
    override val visibility: KtModifierKeywordToken?,
    override val parameters: List<Parameter>,
    override val receiverParameter: Parameter?,
    override val typeParameters: List<TypeParameter>,
    override val replacementMap: MultiMap<KtSimpleNameExpression, IReplacement<KotlinType>>,
    override val controlFlow: ControlFlow<KotlinType>,
    override val returnType: KotlinType,
    override val modifiers: List<KtKeywordToken> = emptyList(),
    val annotations: List<AnnotationDescriptor> = emptyList(),
    override val optInMarkers: List<FqName> = emptyList()
): IExtractableCodeDescriptor<KotlinType> {
    override val name: String get() = suggestedNames.firstOrNull() ?: ""
    override val duplicates: List<DuplicateInfo<KotlinType>> by lazy { findDuplicates() }
    override fun isUnitReturnType(): Boolean {
        return returnType.isUnit()
    }

    override val annotationsText: String
        get() {
            if (annotations.isEmpty()) return ""
            return annotations.map { IdeDescriptorRenderers.SOURCE_CODE.renderAnnotation(it) }.joinToString("\n", postfix = "\n")
        }
}

/**
 * [ExtractableCodeDescriptor.copy] substitute to avoid depending on the [BindingContext] in compile-time.
 *
 * Only used in KTOR IDE plugin.
 */
@ApiStatus.Internal
fun ExtractableCodeDescriptor.withSuggestedNames(
  suggestedNames: List<String>
): ExtractableCodeDescriptor = copy(suggestedNames = suggestedNames)

/**
 * [ExtractableCodeDescriptor.copy] substitute to avoid depending on the [BindingContext] in compile-time.
 *
 * Only used in KTOR IDE plugin.
 */
@ApiStatus.Internal
fun ExtractableCodeDescriptor.withVisibility(
  visibility: KtModifierKeywordToken?
): ExtractableCodeDescriptor = copy(visibility = visibility)

@IDEAPluginsCompatibilityAPI(
    usedIn = [IDEAPlatforms._213],
    message = "Provided for binary backward compatibility",
    plugins = "Jetpack Compose plugin in IDEA"
)
fun ExtractableCodeDescriptor.copy(
    extractionData: ExtractionData = this.extractionData,
    originalContext: BindingContext = this.originalContext,
    suggestedNames: List<String> = this.suggestedNames,
    visibility: KtModifierKeywordToken? = this.visibility,
    parameters: List<Parameter> = this.parameters,
    receiverParameter: Parameter? = this.receiverParameter,
    typeParameters: List<TypeParameter> = this.typeParameters,
    replacementMap: MultiMap<KtSimpleNameExpression, IReplacement<KotlinType>> = this.replacementMap,
    controlFlow: ControlFlow<KotlinType> = this.controlFlow,
    returnType: KotlinType = this.returnType,
    modifiers: List<KtKeywordToken> = this.modifiers,
    annotations: List<AnnotationDescriptor> = this.annotations
) = copy(
    extractionData,
    originalContext,
    suggestedNames,
    visibility,
    parameters,
    receiverParameter,
    typeParameters,
    replacementMap,
    controlFlow,
    returnType,
    modifiers,
    annotations,
    emptyList()
)

fun ExtractableCodeDescriptor.copy(
    newName: String,
    newVisibility: KtModifierKeywordToken?,
    oldToNewParameters: Map<Parameter, Parameter>,
    newReceiver: Parameter?,
    returnType: KotlinType?
): ExtractableCodeDescriptor {
    val newReplacementMap = MultiMap.create<KtSimpleNameExpression, IReplacement<KotlinType>>()
    for ((ref, replacements) in replacementMap.entrySet()) {
        val newReplacements = replacements.map {
            if (it is ParameterReplacement) {
                val parameter = it.parameter
                val newParameter = oldToNewParameters[parameter] ?: return@map it
                it.copy(newParameter)
            } else it
        }
        newReplacementMap.putValues(ref, newReplacements)
    }

    return ExtractableCodeDescriptor(
        extractionData,
        originalContext,
        listOf(newName),
        newVisibility,
        oldToNewParameters.values.filter { it != newReceiver },
        newReceiver,
        typeParameters,
        newReplacementMap,
        controlFlow.copy(oldToNewParameters),
        returnType ?: this.returnType,
        modifiers,
        annotations,
        optInMarkers
    )
}

data class ExtractionGeneratorConfiguration(
    override val descriptor: ExtractableCodeDescriptor,
    override val generatorOptions: ExtractionGeneratorOptions
): IExtractionGeneratorConfiguration<KotlinType>

data class ExtractionResult(
    override val config: ExtractionGeneratorConfiguration,
    override var declaration: KtNamedDeclaration,
    override val duplicateReplacers: Map<KotlinPsiRange, () -> Unit>
) : IExtractionResult<KotlinType> {
    override fun dispose() = unmarkReferencesInside(declaration)
}

data class ExtractableCodeDescriptorWithConflicts(
    override val descriptor: ExtractableCodeDescriptor,
    override val conflicts: MultiMap<PsiElement, String>
): ExtractableCodeDescriptorWithConflictsResult(), IExtractableCodeDescriptorWithConflicts<KotlinType>
