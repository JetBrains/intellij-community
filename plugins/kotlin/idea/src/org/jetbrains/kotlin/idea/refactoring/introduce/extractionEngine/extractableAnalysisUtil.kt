// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.cfg.containingDeclarationForPseudocode
import org.jetbrains.kotlin.cfg.pseudocode.*
import org.jetbrains.kotlin.cfg.pseudocode.instructions.Instruction
import org.jetbrains.kotlin.cfg.pseudocode.instructions.InstructionVisitorWithResult
import org.jetbrains.kotlin.cfg.pseudocode.instructions.InstructionWithNext
import org.jetbrains.kotlin.cfg.pseudocode.instructions.KtElementInstruction
import org.jetbrains.kotlin.cfg.pseudocode.instructions.eval.*
import org.jetbrains.kotlin.cfg.pseudocode.instructions.jumps.*
import org.jetbrains.kotlin.cfg.pseudocode.instructions.special.LocalFunctionDeclarationInstruction
import org.jetbrains.kotlin.cfg.pseudocode.instructions.special.MarkInstruction
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.TraversalOrder
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.TraverseInstructionResult
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.traverse
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.traverseFollowingInstructions
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidator
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.OPT_IN_FQ_NAMES
import org.jetbrains.kotlin.idea.core.compareDescriptors
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.approximateWithResolvableType
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.isResolvableInScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isInsideOf
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsStatement
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getImportableDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.resolveTopLevelClass
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.types.typeUtil.isNothing
import org.jetbrains.kotlin.types.typeUtil.makeNullable
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.*

internal val KotlinBuiltIns.defaultReturnType: KotlinType get() = unitType
internal val KotlinBuiltIns.defaultParameterType: KotlinType get() = nullableAnyType

private fun DeclarationDescriptor.renderForMessage(): String {
    return IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.render(this)
}

private val TYPE_RENDERER = DescriptorRenderer.FQ_NAMES_IN_TYPES.withOptions {
    typeNormalizer = IdeDescriptorRenderers.APPROXIMATE_FLEXIBLE_TYPES
}

private fun KotlinType.renderForMessage(): String = TYPE_RENDERER.renderType(this)

private fun KtDeclaration.renderForMessage(bindingContext: BindingContext): String? =
    bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, this]?.renderForMessage()

private fun List<Instruction>.getModifiedVarDescriptors(bindingContext: BindingContext): Map<VariableDescriptor, List<KtExpression>> {
    val result = HashMap<VariableDescriptor, MutableList<KtExpression>>()
    for (instruction in filterIsInstance<WriteValueInstruction>()) {
        val expression = instruction.element as? KtExpression
        val descriptor = PseudocodeUtil.extractVariableDescriptorIfAny(instruction, bindingContext)
        if (expression != null && descriptor != null) {
            result.getOrPut(descriptor) { ArrayList() }.add(expression)
        }
    }

    return result
}

private fun List<Instruction>.getVarDescriptorsAccessedAfterwards(bindingContext: BindingContext): Set<VariableDescriptor> {
    val accessedAfterwards = HashSet<VariableDescriptor>()
    val visitedInstructions = HashSet<Instruction>()

    fun doTraversal(instruction: Instruction) {
        traverseFollowingInstructions(instruction, visitedInstructions) {
            when {
                it is AccessValueInstruction && it !in this -> PseudocodeUtil.extractVariableDescriptorIfAny(
                    it,
                    bindingContext
                )?.let { descriptor -> accessedAfterwards.add(descriptor) }

                it is LocalFunctionDeclarationInstruction -> doTraversal(it.body.enterInstruction)
            }

            TraverseInstructionResult.CONTINUE
        }
    }

    forEach(::doTraversal)
    return accessedAfterwards
}

private fun List<Instruction>.getExitPoints(): List<Instruction> =
    filter { localInstruction -> localInstruction.nextInstructions.any { it !in this } }

private fun ExtractionData.getResultTypeAndExpressions(
    instructions: List<Instruction>,
    bindingContext: BindingContext,
    targetScope: LexicalScope?,
    options: ExtractionOptions, module: ModuleDescriptor
): Pair<KotlinType, List<KtExpression>> {
    fun instructionToExpression(instruction: Instruction, unwrapReturn: Boolean): KtExpression? {
        return when (instruction) {
            is ReturnValueInstruction ->
                (if (unwrapReturn) null else instruction.returnExpressionIfAny) ?: instruction.returnedValue.element as? KtExpression
            is InstructionWithValue ->
                instruction.outputValue?.element as? KtExpression
            else -> null
        }
    }

    fun instructionToType(instruction: Instruction): KotlinType? {
        val expression = instructionToExpression(instruction, true) ?: return null

        substringInfo?.let {
            if (it.template == expression) return it.type
        }

        if (options.inferUnitTypeForUnusedValues && expression.isUsedAsStatement(bindingContext)) return null

        return bindingContext.getType(expression)
            ?: (expression as? KtReferenceExpression)?.let {
                (bindingContext[BindingContext.REFERENCE_TARGET, it] as? CallableDescriptor)?.returnType
            }
    }

    val resultTypes = instructions.mapNotNull(::instructionToType)
    val commonSupertype = if (resultTypes.isNotEmpty()) CommonSupertypes.commonSupertype(resultTypes) else module.builtIns.defaultReturnType
    val resultType = commonSupertype.approximateWithResolvableType(targetScope, false)

    val expressions = instructions.mapNotNull { instructionToExpression(it, false) }

    return resultType to expressions
}

private fun getCommonNonTrivialSuccessorIfAny(instructions: List<Instruction>): Instruction? {
    val singleSuccessorCheckingVisitor = object : InstructionVisitorWithResult<Boolean>() {
        var target: Instruction? = null

        override fun visitInstructionWithNext(instruction: InstructionWithNext): Boolean {
            return when (instruction) {
                is LoadUnitValueInstruction,
                is MergeInstruction,
                is MarkInstruction -> {
                    instruction.next?.accept(this) ?: true
                }
                else -> visitInstruction(instruction)
            }
        }

        override fun visitJump(instruction: AbstractJumpInstruction): Boolean {
            return when (instruction) {
                is ConditionalJumpInstruction -> visitInstruction(instruction)
                else -> instruction.resolvedTarget?.accept(this) ?: true
            }
        }

        override fun visitInstruction(instruction: Instruction): Boolean {
            if (target != null && target != instruction) return false
            target = instruction
            return true
        }
    }

    if (instructions.flatMap { it.nextInstructions }.any { !it.accept(singleSuccessorCheckingVisitor) }) return null
    return singleSuccessorCheckingVisitor.target ?: instructions.firstOrNull()?.owner?.sinkInstruction
}

private fun ExtractionData.getLocalDeclarationsWithNonLocalUsages(
    pseudocode: Pseudocode,
    localInstructions: List<Instruction>,
    bindingContext: BindingContext
): List<KtNamedDeclaration> {
    val declarations = HashSet<KtNamedDeclaration>()
    pseudocode.traverse(TraversalOrder.FORWARD) { instruction ->
        if (instruction !in localInstructions) {
            instruction.getPrimaryDeclarationDescriptorIfAny(bindingContext)?.let { descriptor ->
                val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor)
                if (declaration is KtNamedDeclaration && declaration.isInsideOf(physicalElements)) {
                    declarations.add(declaration)
                }
            }
        }
    }
    return declarations.sortedBy { it.textRange!!.startOffset }
}

private fun KotlinType.isExtractable(targetScope: LexicalScope?): Boolean {
    return processTypeIfExtractable(
        typeParameters = mutableSetOf(),
        nonDenotableTypes = mutableSetOf(),
        targetScope = targetScope,
        processTypeArguments = true
    )
}

internal fun KotlinType.processTypeIfExtractable(
    typeParameters: MutableSet<TypeParameter>,
    nonDenotableTypes: MutableSet<KotlinType>,
    targetScope: LexicalScope?,
    processTypeArguments: Boolean = true
): Boolean {
    return processTypeIfExtractable(typeParameters, nonDenotableTypes, processTypeArguments, { type -> type.arguments.map { it.type } } ) { typeToCheck, typeParameters ->
        val parameterTypeDescriptor = typeToCheck.constructor.declarationDescriptor as? TypeParameterDescriptor
        val typeParameter = parameterTypeDescriptor?.let {
            DescriptorToSourceUtils.descriptorToDeclaration(it)
        } as? KtTypeParameter
        when {
            typeToCheck.isResolvableInScope(targetScope, true) -> true
            typeParameter != null -> {
                typeParameters.add(TypeParameter(typeParameter, typeParameter.collectRelevantConstraints()))
                true
            }
            else -> false
        }
    }
}

internal class MutableParameter(
    override val argumentText: String,
    override val originalDescriptor: DeclarationDescriptor,
    override val receiverCandidate: Boolean,
    private val targetScope: LexicalScope?,
    private val originalType: KotlinType,
    private val possibleTypes: Set<KotlinType>
) : Parameter, IMutableParameter<KotlinType> {
    // All modifications happen in the same thread
    private var writable: Boolean = true
    private val defaultTypes = LinkedHashSet<KotlinType>()
    private val typePredicates = HashSet<TypePredicate>()

    override var refCount: Int = 0

    fun addDefaultType(kotlinType: KotlinType) {
        assert(writable) { "Can't add type to non-writable parameter $currentName" }

        if (kotlinType in possibleTypes) {
            defaultTypes.add(kotlinType)
        }
    }

    fun addTypePredicate(predicate: TypePredicate) {
        assert(writable) { "Can't add type predicate to non-writable parameter $currentName" }
        typePredicates.add(predicate)
    }

    var currentName: String? = null
    override val name: String get() = currentName!!

    override var mirrorVarName: String? = null

    private val defaultType: KotlinType by lazy {
        writable = false
        if (defaultTypes.isNotEmpty()) {
            TypeIntersector.intersectTypes(defaultTypes)!!
        } else originalType
    }

    private val allParameterTypeCandidates: List<KotlinType> by lazy {
        writable = false

        val typePredicate = and(typePredicates)

        val typeSet = if (defaultType.isFlexible()) {
            val bounds = defaultType.asFlexibleType()
            LinkedHashSet<KotlinType>().apply {
                if (typePredicate(bounds.upperBound)) add(bounds.upperBound)
                if (typePredicate(bounds.lowerBound)) add(bounds.lowerBound)
            }
        } else linkedSetOf(defaultType)

        val addNullableTypes = defaultType.isNullabilityFlexible() && typeSet.size > 1
        val superTypes = TypeUtils.getAllSupertypes(defaultType).filter(typePredicate)

        for (superType in superTypes) {
            if (addNullableTypes) {
                typeSet.add(superType.makeNullable())
            }
            typeSet.add(superType)
        }

        typeSet.toList()
    }

    override fun getParameterTypeCandidates(): List<KotlinType> {
        return allParameterTypeCandidates.filter { it.isExtractable(targetScope) }
    }

    override val parameterType: KotlinType
        get() = getParameterTypeCandidates().firstOrNull() ?: defaultType

}

private fun ExtractionData.getLocalInstructions(pseudocode: Pseudocode?): List<Instruction>? {
    if (pseudocode == null) return null
    val instructions = ArrayList<Instruction>()
    pseudocode.traverse(TraversalOrder.FORWARD) {
        if (it is KtElementInstruction && it.element.isInsideOf(physicalElements)) {
            instructions.add(it)
        }
    }
    return instructions
}

private data class ExperimentalMarkers(
    val propagatingMarkerDescriptors: List<AnnotationDescriptor>,
    val optInMarkers: List<FqName>
) {
    companion object {
        val empty = ExperimentalMarkers(emptyList(), emptyList())
    }
}

private fun ExtractionData.getExperimentalMarkers(): ExperimentalMarkers {
    fun AnnotationDescriptor.isExperimentalMarker(): Boolean {
        if (fqName == null) return false
        val annotations = annotationClass?.annotations ?: return false
        return annotations.hasAnnotation(OptInNames.REQUIRES_OPT_IN_FQ_NAME) ||
                annotations.hasAnnotation(FqNames.OptInFqNames.OLD_EXPERIMENTAL_FQ_NAME)
    }

    val bindingContext = bindingContext ?: return ExperimentalMarkers.empty
    val container = commonParent.getStrictParentOfType<KtNamedFunction>() ?: return ExperimentalMarkers.empty

    val propagatingMarkerDescriptors = mutableListOf<AnnotationDescriptor>()
    val optInMarkerNames = mutableListOf<FqName>()
    for (annotationEntry in container.annotationEntries) {
        val annotationDescriptor = bindingContext[BindingContext.ANNOTATION, annotationEntry] ?: continue
        val fqName = annotationDescriptor.fqName ?: continue

        if (fqName in OptInNames.OPT_IN_FQ_NAMES) {
            for (argument in annotationEntry.valueArguments) {
                val argumentExpression = argument.getArgumentExpression()?.safeAs<KtClassLiteralExpression>() ?: continue
                val markerFqName = bindingContext[
                        BindingContext.REFERENCE_TARGET,
                        argumentExpression.lhs?.safeAs<KtNameReferenceExpression>()
                ]?.fqNameSafe ?: continue
                optInMarkerNames.add(markerFqName)
            }
        } else if (annotationDescriptor.isExperimentalMarker()) {
            propagatingMarkerDescriptors.add(annotationDescriptor)
        }
    }

    val requiredMarkers = mutableSetOf<FqName>()
    if (propagatingMarkerDescriptors.isNotEmpty() || optInMarkerNames.isNotEmpty()) {
        originalElements.forEach { element ->
            element.accept(object : KtTreeVisitorVoid() {
                override fun visitReferenceExpression(expression: KtReferenceExpression) {
                    val descriptor = bindingContext[BindingContext.REFERENCE_TARGET, expression]
                    if (descriptor != null) {
                        for (descr in setOf(descriptor, descriptor.getImportableDescriptor())) {
                            for (ann in descr.annotations) {
                                val fqName = ann.fqName ?: continue
                                if (ann.isExperimentalMarker()) {
                                    requiredMarkers.add(fqName)
                                }
                            }
                        }
                    }
                    super.visitReferenceExpression(expression)
                }
            })
        }
    }

    return ExperimentalMarkers(
        propagatingMarkerDescriptors.filter { it.fqName in requiredMarkers },
        optInMarkerNames.filter { it in requiredMarkers }
    )
}

class KotlinTypeDescriptor(private val data: ExtractionData) : TypeDescriptor<KotlinType> {
    private val module = data.commonParent.containingKtFile.findModuleDescriptor()

    override fun KotlinType.isMeaningful(): Boolean = !KotlinBuiltIns.isUnit(this) && !KotlinBuiltIns.isNothing(this)

    override fun KotlinType.isError(): Boolean = isError

    override val booleanType: KotlinType = module.builtIns.booleanType
    override val unitType: KotlinType = module.builtIns.unitType
    override val nothingType: KotlinType = module.builtIns.nothingType
    override val nullableAnyType: KotlinType = module.builtIns.nullableAnyType

    override fun createListType(argTypes: List<KotlinType>): KotlinType {
        return TypeUtils.substituteParameters(
            module.builtIns.list,
            Collections.singletonList(CommonSupertypes.commonSupertype(argTypes))
        )
    }

    override fun createTuple(outputValues: List<OutputValue<KotlinType>>): KotlinType {
        val boxingClass = when (outputValues.size) {
            1 -> return outputValues.first().valueType
            2 -> module.resolveTopLevelClass(FqName("kotlin.Pair"), NoLookupLocation.FROM_IDE)!!
            3 -> module.resolveTopLevelClass(FqName("kotlin.Triple"), NoLookupLocation.FROM_IDE)!!
            else -> return module.builtIns.defaultReturnType
        }
        return TypeUtils.substituteParameters(boxingClass, outputValues.map { it.valueType })
    }

    override fun returnType(ktNamedDeclaration: KtNamedDeclaration): KotlinType? {
        val descriptor = data.bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, ktNamedDeclaration] as? CallableDescriptor
        return descriptor?.returnType
    }

    override fun typeArguments(kotlinType: KotlinType): List<KotlinType> {
        return kotlinType.arguments.map { it.type }
    }

    override fun renderType(
        kotlinType: KotlinType,
        isReceiver: Boolean,
        variance: Variance
    ): String {
        val renderType = IdeDescriptorRenderers.SOURCE_CODE.renderType(kotlinType)
        return if ((kotlinType.isFunctionType || kotlinType.isSuspendFunctionType) && isReceiver) "($renderType)" else renderType
    }

    override fun renderTypeWithoutApproximation(kotlinType: KotlinType): String {
        return kotlinType.renderForMessage()
    }

    override fun renderForMessage(ktNamedDeclaration: KtNamedDeclaration): String? {
        return data.bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, ktNamedDeclaration]?.renderForMessage()
    }

    override fun renderForMessage(param: IParameter<KotlinType>): String {
        return (param as Parameter).originalDescriptor.renderForMessage()
    }
    private val targetScope = data.targetSibling.getResolutionScope(
        data.bindingContext,
        data.commonParent.getResolutionFacade()
    )

    override fun isResolvableInScope(
        typeToCheck: KotlinType,
        typeParameters: MutableSet<TypeParameter>
    ): Boolean {
        val parameterTypeDescriptor = typeToCheck.constructor.declarationDescriptor as? TypeParameterDescriptor
        val typeParameter = parameterTypeDescriptor?.let {
            DescriptorToSourceUtils.descriptorToDeclaration(it)
        } as? KtTypeParameter
        return when {
            typeToCheck.isResolvableInScope(targetScope, true) -> true
            typeParameter != null -> {
                typeParameters.add(TypeParameter(typeParameter, typeParameter.collectRelevantConstraints()))
                true
            }
            else -> false
        }
    }
}

object ExtractNameSuggester : IExtractionNameSuggester<KotlinType> {

    override fun suggestNamesByType(
        kotlinType: KotlinType,
        container: KtElement,
        validator: KotlinNameValidator,
        defaultName: String?,
    ): List<String> = if (KotlinBuiltIns.isUnit(kotlinType)) emptyList()
    else Fe10KotlinNameSuggester.suggestNamesByType(kotlinType, validator, defaultName)

    override fun suggestNameByName(
        name: String,
        validator: KotlinNameValidator,
    ): String = Fe10KotlinNameSuggester.suggestNameByName(name, validator)
}

private class ExtractionDataAnalyzer(private val extractionData: ExtractionData): AbstractExtractionDataAnalyzer<KotlinType, MutableParameter>(extractionData) {

    private val pseudocode = extractionData.commonParent.containingDeclarationForPseudocode?.getContainingPseudocode(extractionData.bindingContext)

    override fun hasSyntaxErrors(): Boolean {
        return pseudocode == null
    }

    private val localInstructions = extractionData.getLocalInstructions(pseudocode)

    private val targetScope = extractionData.targetSibling.getResolutionScope(
        extractionData.bindingContext,
        extractionData.commonParent.getResolutionFacade()
    )

    private val modifiedVarDescriptorsWithExpressions = localInstructions?.getModifiedVarDescriptors(extractionData.bindingContext)

    override fun getLocalDeclarationsWithNonLocalUsages(): List<KtNamedDeclaration> {
        return extractionData.getLocalDeclarationsWithNonLocalUsages(pseudocode!!, localInstructions!!, extractionData.bindingContext)
    }

    override fun getVarDescriptorsAccessedAfterwards(): Set<String> {
        return localInstructions!!.getVarDescriptorsAccessedAfterwards(extractionData.bindingContext).map { it.name.asString() }.toSet()
    }

    override fun getModifiedVars(): Map<String, List<KtExpression>> {
        return localInstructions!!.getModifiedVarDescriptors(extractionData.bindingContext).mapKeys { it.key.name.asString() }
    }

    override fun createOutputDescriptor(): OutputDescriptor<KotlinType> {
        return extractionData.createOutputDescriptor(pseudocode!!, localInstructions!!, targetScope)
    }

    override val nameSuggester: IExtractionNameSuggester<KotlinType> = ExtractNameSuggester

    override val typeDescriptor: TypeDescriptor<KotlinType> = KotlinTypeDescriptor(extractionData)

    override fun inferParametersInfo(
        virtualBlock: KtBlockExpression,
        modifiedVariables: Set<String>
    ): ParametersInfo<KotlinType, MutableParameter> {
        return extractionData.inferParametersInfo(
            virtualBlock,
            pseudocode!!,
            extractionData.bindingContext,
            targetScope,
            modifiedVarDescriptorsWithExpressions!!.keys
        )
    }

    override fun createDescriptor(
        suggestedFunctionNames: List<String>,
        defaultVisibility: KtModifierKeywordToken?,
        parameters: List<MutableParameter>,
        receiverParameter: MutableParameter?,
        typeParameters: List<TypeParameter>,
        replacementMap: MultiMap<KtSimpleNameExpression, IReplacement<KotlinType>>,
        flow: ControlFlow<KotlinType>,
        returnType: KotlinType
    ): IExtractableCodeDescriptor<KotlinType> {
        val experimentalMarkers = extractionData.getExperimentalMarkers()
        var descriptor = ExtractableCodeDescriptor(
          extractionData,
          extractionData.bindingContext,
          suggestedFunctionNames,
          defaultVisibility,
          parameters,
          receiverParameter as Parameter?,
          typeParameters,
          replacementMap,
          flow,
          returnType,
          emptyList(),
          annotations = experimentalMarkers.propagatingMarkerDescriptors,
          optInMarkers = experimentalMarkers.optInMarkers
        )

        val generatedDeclaration = ExtractionGeneratorConfiguration(
            descriptor,
            ExtractionGeneratorOptions(inTempFile = true, allowExpressionBody = false)
        ).generateDeclaration().declaration
        val virtualContext = generatedDeclaration.analyzeWithContent()
        if (virtualContext.diagnostics.all()
                .any { it.factory == Errors.ILLEGAL_SUSPEND_FUNCTION_CALL || it.factory == Errors.ILLEGAL_SUSPEND_PROPERTY_ACCESS }
        ) {
            descriptor = descriptor.copy(modifiers = listOf(KtTokens.SUSPEND_KEYWORD))
        }

        for (analyser in AdditionalExtractableAnalyser.EP_NAME.extensions) {
            descriptor = analyser.amendDescriptor(descriptor)
        }
        return descriptor
    }
}

fun ExtractionData.performAnalysis(): AnalysisResult<KotlinType> {
    return ExtractionDataAnalyzer(this).performAnalysis()
}

private fun ExtractionData.createOutputDescriptor(pseudocode: Pseudocode, localInstructions: List<Instruction>, targetScope: LexicalScope): OutputDescriptor<KotlinType> {

    val valuedReturnExits = ArrayList<ReturnValueInstruction>()
    val defaultExits = ArrayList<Instruction>()
    val jumpExits = ArrayList<AbstractJumpInstruction>()
    localInstructions.getExitPoints().forEach {
        val e = (it as? UnconditionalJumpInstruction)?.element

        when (val inst = when {
            it !is ReturnValueInstruction && it !is ReturnNoValueInstruction && it.owner != pseudocode -> null
            it is UnconditionalJumpInstruction && it.targetLabel.isJumpToError -> it
            e != null && e !is KtBreakExpression && e !is KtContinueExpression -> it.previousInstructions.firstOrNull()
            else -> it
        }) {
            is ReturnValueInstruction -> if (inst.owner == pseudocode) {
                if (inst.returnExpressionIfAny == null) {
                    defaultExits.add(inst)
                } else {
                    valuedReturnExits.add(inst)
                }
            }

            is AbstractJumpInstruction -> {
                val element = inst.element
                if ((element is KtReturnExpression && inst.owner == pseudocode)
                    || element is KtBreakExpression
                    || element is KtContinueExpression
                ) {
                    jumpExits.add(inst)
                } else if (element !is KtThrowExpression && !inst.targetLabel.isJumpToError) defaultExits.add(inst)
            }

            else -> if (inst != null && inst !is LocalFunctionDeclarationInstruction) defaultExits.add(inst)
        }
    }

    val module = originalFile.findModuleDescriptor()
    val (typeOfDefaultFlow, defaultResultExpressions) = getResultTypeAndExpressions(
        defaultExits,
        bindingContext,
        targetScope,
        options,
        module
    )

    val (returnValueType, valuedReturnExpressions) = getResultTypeAndExpressions(
        valuedReturnExits,
        bindingContext,
        targetScope,
        options,
        module
    )

    val jumpTarget = getCommonNonTrivialSuccessorIfAny(jumpExits)

    return OutputDescriptor(
        defaultResultExpression = defaultResultExpressions.singleOrNull(),
        typeOfDefaultFlow = typeOfDefaultFlow,
        implicitReturn = null,
        lastExpressionHasNothingType = expressions.lastOrNull()?.let { bindingContext.getType(it)?.isNothing() } == true,
        valuedReturnExpressions = valuedReturnExpressions,
        returnValueType = returnValueType,
        jumpExpressions = jumpExits.map { it.element as KtExpression },
        hasSingleTarget = valuedReturnExits.isNotEmpty() && getCommonNonTrivialSuccessorIfAny(valuedReturnExits) != null ||
                jumpExits.isNotEmpty() && jumpTarget != null,
        sameExitForDefaultAndJump = getCommonNonTrivialSuccessorIfAny(defaultExits) == jumpTarget
    )
}

@JvmOverloads
fun ExtractableCodeDescriptor.validate(target: ExtractionTarget = ExtractionTarget.FUNCTION): ExtractableCodeDescriptorWithConflicts {
    fun getDeclarationMessage(declaration: PsiElement, messageKey: String, capitalize: Boolean = true): String {
        val declarationStr = RefactoringUIUtil.getDescription(declaration, true)
        val message = KotlinBundle.message(messageKey, declarationStr)
        return if (capitalize) message.capitalize() else message
    }

    val conflicts = MultiMap<PsiElement, String>()

    val result = ExtractionGeneratorConfiguration(
        this,
        ExtractionGeneratorOptions(inTempFile = true, allowExpressionBody = false, target = target)
    ).generateDeclaration()

    val valueParameterList = (result.declaration as? KtNamedFunction)?.valueParameterList
    val typeParameterList = (result.declaration as? KtNamedFunction)?.typeParameterList
    val generatedDeclaration = result.declaration
    val bindingContext = generatedDeclaration.analyzeWithContent()

    fun processReference(currentRefExpr: KtSimpleNameExpression) {
        val resolveResult = currentRefExpr.resolveResult as? ResolveResult<DeclarationDescriptor, ResolvedCall<*>>?: return
        if (currentRefExpr.parent is KtThisExpression) return

        val diagnostics = bindingContext.diagnostics.forElement(currentRefExpr)

        val currentDescriptor = bindingContext[BindingContext.REFERENCE_TARGET, currentRefExpr]
        val currentTarget =
            currentDescriptor?.let { DescriptorToSourceUtilsIde.getAnyDeclaration(extractionData.project, it) } as? PsiNamedElement
        if (currentTarget is KtParameter && currentTarget.parent == valueParameterList) return
        if (currentTarget is KtTypeParameter && currentTarget.parent == typeParameterList) return
        if (currentDescriptor is LocalVariableDescriptor
            && parameters.any { it.mirrorVarName == currentDescriptor.name.asString() }
        ) return

        if (diagnostics.any { it.factory in Errors.UNRESOLVED_REFERENCE_DIAGNOSTICS }
            || (currentDescriptor != null
                    && !ErrorUtils.isError(currentDescriptor)
                    && !compareDescriptors(extractionData.project, currentDescriptor, resolveResult.descriptor))) {
            conflicts.putValue(
                resolveResult.originalRefExpr,
                getDeclarationMessage(resolveResult.declaration, "0.will.no.longer.be.accessible.after.extraction")
            )
            return
        }

        diagnostics.firstOrNull { it.factory in Errors.INVISIBLE_REFERENCE_DIAGNOSTICS }?.let {
            val message = when (it.factory) {
                Errors.INVISIBLE_SETTER ->
                    getDeclarationMessage(resolveResult.declaration, "setter.of.0.will.become.invisible.after.extraction", false)
                else ->
                    getDeclarationMessage(resolveResult.declaration, "0.will.become.invisible.after.extraction")
            }
            conflicts.putValue(resolveResult.originalRefExpr, message)
        }
    }

    result.declaration.accept(
        object : KtTreeVisitorVoid() {
            override fun visitUserType(userType: KtUserType) {
                val refExpr = userType.referenceExpression ?: return
                val diagnostics = bindingContext.diagnostics.forElement(refExpr)
                diagnostics.firstOrNull { it.factory == Errors.INVISIBLE_REFERENCE }?.let {
                    val declaration = refExpr.mainReference.resolve() as? PsiNamedElement ?: return
                    conflicts.putValue(declaration, getDeclarationMessage(declaration, "0.will.become.invisible.after.extraction"))
                }
            }

            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                processReference(expression)
            }
        }
    )

    return ExtractableCodeDescriptorWithConflicts(this, conflicts)
}
