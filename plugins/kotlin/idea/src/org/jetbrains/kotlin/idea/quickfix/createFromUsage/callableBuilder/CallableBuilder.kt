// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInsight.navigation.activateFileWithPsiElement
import com.intellij.codeInsight.template.*
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.UnfairTextRange
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.cfg.pseudocode.Pseudocode
import org.jetbrains.kotlin.cfg.pseudocode.getContainingPseudocode
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.MutablePackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.base.psi.getReturnTypeReference
import org.jetbrains.kotlin.idea.base.psi.getReturnTypeReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.idea.codeinsight.utils.ValVarExpression
import org.jetbrains.kotlin.idea.core.*
import org.jetbrains.kotlin.idea.createFromUsage.setupEditorSelection
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.ClassKind
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateClassUtil
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageUtil
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.TransformToJavaUtil.transformToJavaMemberIfApplicable
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.idea.util.DialogWithEditor
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.getDefaultInitializer
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassOrAny
import org.jetbrains.kotlin.resolve.scopes.HierarchicalScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScopeImpl
import org.jetbrains.kotlin.resolve.scopes.LexicalScopeKind
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier
import org.jetbrains.kotlin.resolve.scopes.utils.memberScopeAsImportingScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeProjectionImpl
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.isAnyOrNullableAny
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.types.typeUtil.makeNullable
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import java.util.*

/**
 * Represents a single choice for a type (e.g. parameter type or return type).
 */
class TypeCandidate(val theType: KotlinType, scope: HierarchicalScope? = null) {
    val typeParameters: Array<TypeParameterDescriptor>
    var renderedTypes: List<String> = emptyList()
        private set
    var renderedTypeParameters: List<RenderedTypeParameter>? = null
        private set

    fun render(typeParameterNameMap: Map<TypeParameterDescriptor, String>, fakeFunction: FunctionDescriptor?) {
        renderedTypes = theType.renderShort(typeParameterNameMap)
        renderedTypeParameters = typeParameters.map {
            RenderedTypeParameter(it, it.containingDeclaration == fakeFunction, typeParameterNameMap.getValue(it))
        }
    }

    init {
        val typeParametersInType = theType.getTypeParameters()
        if (scope == null) {
            typeParameters = typeParametersInType.toTypedArray()
            renderedTypes = theType.renderShort(Collections.emptyMap())
        } else {
            typeParameters = getTypeParameterNamesNotInScope(typeParametersInType, scope).toTypedArray()
        }
    }

    override fun toString(): String = theType.toString()
}

data class RenderedTypeParameter(
    val typeParameter: TypeParameterDescriptor,
    val fake: Boolean,
    val text: String
)

fun List<TypeCandidate>.getTypeByRenderedType(renderedTypes: List<String>): KotlinType? =
    firstOrNull { it.renderedTypes == renderedTypes }?.theType

class CallableBuilderConfiguration(
    val callableInfos: List<CallableInfo>,
    val originalElement: KtElement,
    val currentFile: KtFile = originalElement.containingKtFile,
    val currentEditor: Editor? = null,
    val isExtension: Boolean = false,
    val enableSubstitutions: Boolean = true
)

sealed class CallablePlacement {
    class WithReceiver(val receiverTypeCandidate: TypeCandidate) : CallablePlacement()
    class NoReceiver(val containingElement: PsiElement) : CallablePlacement()
}

class CallableBuilder(val config: CallableBuilderConfiguration) {
    private var finished: Boolean = false

    val currentFileContext: BindingContext = config.currentFile.analyzeWithContent()

    private lateinit var _currentFileModule: ModuleDescriptor
    val currentFileModule: ModuleDescriptor
        get() {
            if (!_currentFileModule.isValid) {
                updateCurrentModule()
            }
            return _currentFileModule
        }

    init {
        updateCurrentModule()
    }

    val pseudocode: Pseudocode? by lazy { config.originalElement.getContainingPseudocode(currentFileContext) }

    private val typeCandidates = HashMap<TypeInfo, List<TypeCandidate>>()

    var placement: CallablePlacement? = null

    var elementToReplace: PsiElement? = null

    var isStartTemplate: Boolean = true

    private val elementsToShorten = ArrayList<KtElement>()

    private fun updateCurrentModule() {
        _currentFileModule = config.currentFile.analyzeWithAllCompilerChecks().moduleDescriptor
    }

    fun computeTypeCandidates(typeInfo: TypeInfo): List<TypeCandidate> {
        return typeCandidates.getOrPut(typeInfo) { typeInfo.getPossibleTypes(this).map { TypeCandidate(it) } }
    }

    private fun computeTypeCandidates(
        typeInfo: TypeInfo,
        substitutions: List<KotlinTypeSubstitution>,
        scope: HierarchicalScope
    ): List<TypeCandidate> {
        if (!typeInfo.substitutionsAllowed) return computeTypeCandidates(typeInfo)
        return typeCandidates.getOrPut(typeInfo) {
            val types = typeInfo.getPossibleTypes(this).asReversed()

            // We have to use semantic equality here
            data class EqWrapper(val _type: KotlinType) {
                override fun equals(other: Any?) = this === other
                        || other is EqWrapper && KotlinTypeChecker.DEFAULT.equalTypes(_type, other._type)

                override fun hashCode() = 0 // no good way to compute hashCode() that would agree with our equals()
            }

            val newTypes = LinkedHashSet(types.map(::EqWrapper))
            for (substitution in substitutions) {
                // each substitution can be applied or not, so we offer all options
                val toAdd = newTypes.map { it._type.substitute(substitution, typeInfo.variance) }
                // substitution.byType are type arguments, but they cannot already occur in the type before substitution
                val toRemove = newTypes.filter { substitution.byType in it._type }

                newTypes.addAll(toAdd.map(::EqWrapper))
                newTypes.removeAll(toRemove)
            }

            if (newTypes.isEmpty()) {
                newTypes.add(EqWrapper(currentFileModule.builtIns.anyType))
            }

            newTypes.map { TypeCandidate(it._type, scope) }.asReversed()
        }
    }

    private fun buildNext(iterator: Iterator<CallableInfo>) {
        if (iterator.hasNext()) {
            val context = Context(iterator.next())
            val action: () -> Unit = { context.buildAndRunTemplate { buildNext(iterator) } }
            if (IntentionPreviewUtils.isPreviewElement(config.currentFile)) {
                action()
            } else {
                runWriteAction(action)
                ApplicationManager.getApplication().invokeLater { context.showDialogIfNeeded() }
            }
        } else {
            val action: () -> Unit = { ShortenReferences.DEFAULT.process(elementsToShorten) }
            if (IntentionPreviewUtils.isPreviewElement(config.currentFile)) {
                action()
            } else {
                runWriteAction(action)
            }
        }
    }

    fun build(onFinish: () -> Unit = {}) {
        try {
            assert(config.currentEditor != null) { "Can't run build() without editor" }
            check(!finished) { "Current builder has already finished" }
            buildNext(config.callableInfos.iterator())
        } finally {
            finished = true
            onFinish()
        }
    }

    private inner class Context(val callableInfo: CallableInfo) {
        val skipReturnType: Boolean
        val ktFileToEdit: KtFile
        val containingFileEditor: Editor
        val containingElement: PsiElement
        val dialogWithEditor: DialogWithEditor?
        val receiverClassDescriptor: ClassifierDescriptor?
        val typeParameterNameMap: Map<TypeParameterDescriptor, String>
        val receiverTypeCandidate: TypeCandidate?
        val mandatoryTypeParametersAsCandidates: List<TypeCandidate>
        val substitutions: List<KotlinTypeSubstitution>
        var finished: Boolean = false

        init {
            // gather relevant information

            val placement = placement
            var nullableReceiver = false
            when (placement) {
                is CallablePlacement.NoReceiver -> {
                    containingElement = placement.containingElement
                    receiverClassDescriptor =
                        when (containingElement) {
                            is KtClassOrObject -> currentFileContext[BindingContext.CLASS, containingElement]
                            is PsiClass -> containingElement.getJavaClassDescriptor()
                            else -> null
                        }
                }
                is CallablePlacement.WithReceiver -> {
                    val theType = placement.receiverTypeCandidate.theType
                    nullableReceiver = theType.isMarkedNullable
                    receiverClassDescriptor = theType.constructor.declarationDescriptor
                    val classDeclaration = receiverClassDescriptor?.let { DescriptorToSourceUtils.getSourceFromDescriptor(it) }
                    containingElement = if (!config.isExtension && classDeclaration != null) classDeclaration else config.currentFile
                }
                else -> throw IllegalArgumentException("Placement wasn't initialized")
            }
            val receiverType = receiverClassDescriptor?.defaultType?.let {
                if (nullableReceiver) it.makeNullable() else it
            }

            val project = config.currentFile.project

            if (containingElement.containingFile != config.currentFile) {
                activateFileWithPsiElement(containingElement)
            }

            dialogWithEditor = if (containingElement is KtElement) {
                ktFileToEdit = containingElement.containingKtFile
                containingFileEditor = if (ktFileToEdit != config.currentFile) {
                    FileEditorManager.getInstance(project).selectedTextEditor ?: config.currentEditor!!
                } else {
                    config.currentEditor!!
                }
                null
            } else {
                val dialog = object : DialogWithEditor(project, KotlinBundle.message("fix.create.from.usage.dialog.title"), "") {
                    override fun doOKAction() {
                        project.executeWriteCommand(KotlinBundle.message("premature.end.of.template")) {
                            TemplateManagerImpl.getTemplateState(editor)?.gotoEnd(false)
                        }
                        super.doOKAction()
                    }
                }
                containingFileEditor = dialog.editor
                containingFileEditor.settings.additionalColumnsCount = config.currentEditor!!.settings.getRightMargin(project)
                containingFileEditor.settings.additionalLinesCount = 5
                ktFileToEdit = PsiDocumentManager.getInstance(project).getPsiFile(containingFileEditor.document) as KtFile
                ktFileToEdit.analysisContext = config.currentFile
                dialog
            }

            val scope = getDeclarationScope()

            receiverTypeCandidate = receiverType?.let { TypeCandidate(it, scope) }

            val fakeFunction: FunctionDescriptor?
            // figure out type substitutions for type parameters
            val substitutionMap = LinkedHashMap<KotlinType, KotlinType>()
            if (config.enableSubstitutions) {
                collectSubstitutionsForReceiverTypeParameters(receiverType, substitutionMap)
                val typeArgumentsForFakeFunction = callableInfo.typeParameterInfos
                    .map {
                        val typeCandidates = computeTypeCandidates(it)
                        assert(typeCandidates.size == 1) { "Ambiguous type candidates for type parameter $it: $typeCandidates" }
                        typeCandidates.first().theType
                    }
                    .subtract(substitutionMap.keys)
                fakeFunction = createFakeFunctionDescriptor(scope, typeArgumentsForFakeFunction.size)
                collectSubstitutionsForCallableTypeParameters(fakeFunction, typeArgumentsForFakeFunction, substitutionMap)
                mandatoryTypeParametersAsCandidates = listOfNotNull(receiverTypeCandidate) + typeArgumentsForFakeFunction.map {
                    TypeCandidate(substitutionMap[it]!!, scope)
                }
            } else {
                fakeFunction = null
                mandatoryTypeParametersAsCandidates = Collections.emptyList()
            }
            substitutions = substitutionMap.map { KotlinTypeSubstitution(it.key, it.value) }

            callableInfo.parameterInfos.forEach {
                computeTypeCandidates(it.typeInfo, substitutions, scope)
            }

            val returnTypeCandidate = computeTypeCandidates(callableInfo.returnTypeInfo, substitutions, scope).singleOrNull()
            skipReturnType = when (callableInfo.kind) {
                CallableKind.FUNCTION ->
                    returnTypeCandidate?.theType?.isUnit() ?: false
                CallableKind.CLASS_WITH_PRIMARY_CONSTRUCTOR ->
                    callableInfo.returnTypeInfo == TypeInfo.Empty || returnTypeCandidate?.theType?.isAnyOrNullableAny() ?: false
                CallableKind.CONSTRUCTOR -> true
                CallableKind.PROPERTY -> containingElement is KtBlockExpression
            }

            // figure out type parameter renames to avoid conflicts
            typeParameterNameMap = getTypeParameterRenames(scope)
            callableInfo.parameterInfos.forEach { renderTypeCandidates(it.typeInfo, typeParameterNameMap, fakeFunction) }
            if (!skipReturnType) {
                renderTypeCandidates(callableInfo.returnTypeInfo, typeParameterNameMap, fakeFunction)
            }
            receiverTypeCandidate?.render(typeParameterNameMap, fakeFunction)
            mandatoryTypeParametersAsCandidates.forEach { it.render(typeParameterNameMap, fakeFunction) }
        }

        private fun getDeclarationScope(): HierarchicalScope {
            if (config.isExtension || receiverClassDescriptor == null) {
                return currentFileModule.getPackage(config.currentFile.packageFqName).memberScope.memberScopeAsImportingScope()
            }

            if (receiverClassDescriptor is ClassDescriptorWithResolutionScopes) {
                return receiverClassDescriptor.scopeForMemberDeclarationResolution
            }

            assert(receiverClassDescriptor is JavaClassDescriptor) { "Unexpected receiver class: $receiverClassDescriptor" }

            val projections = ((receiverClassDescriptor as JavaClassDescriptor).declaredTypeParameters)
                .map { TypeProjectionImpl(it.defaultType) }
            val memberScope = receiverClassDescriptor.getMemberScope(projections)

            return LexicalScopeImpl(
                memberScope.memberScopeAsImportingScope(), receiverClassDescriptor, false, null, emptyList(),
                LexicalScopeKind.SYNTHETIC
            ) {
                receiverClassDescriptor.typeConstructor.parameters.forEach { addClassifierDescriptor(it) }
            }
        }

        private fun collectSubstitutionsForReceiverTypeParameters(
            receiverType: KotlinType?,
            result: MutableMap<KotlinType, KotlinType>
        ) {
            if (placement is CallablePlacement.NoReceiver) return

            val classTypeParameters = receiverType?.arguments ?: Collections.emptyList()
            val ownerTypeArguments = (placement as? CallablePlacement.WithReceiver)?.receiverTypeCandidate?.theType?.arguments
                ?: Collections.emptyList()
            assert(ownerTypeArguments.size == classTypeParameters.size)
            ownerTypeArguments.zip(classTypeParameters).forEach { result[it.first.type] = it.second.type }
        }

        private fun collectSubstitutionsForCallableTypeParameters(
            fakeFunction: FunctionDescriptor,
            typeArguments: Set<KotlinType>,
            result: MutableMap<KotlinType, KotlinType>
        ) {
            for ((typeArgument, typeParameter) in typeArguments.zip(fakeFunction.typeParameters)) {
                result[typeArgument] = typeParameter.defaultType
            }
        }

        @OptIn(FrontendInternals::class)
        private fun createFakeFunctionDescriptor(scope: HierarchicalScope, typeParameterCount: Int): FunctionDescriptor {
            val fakeFunction = SimpleFunctionDescriptorImpl.create(
                MutablePackageFragmentDescriptor(currentFileModule, FqName("fake")),
                Annotations.EMPTY,
                Name.identifier("fake"),
                CallableMemberDescriptor.Kind.SYNTHESIZED,
                SourceElement.NO_SOURCE
            )

            val validator = CollectingNameValidator { scope.findClassifier(Name.identifier(it), NoLookupLocation.FROM_IDE) == null }
            val parameterNames = Fe10KotlinNameSuggester.suggestNamesForTypeParameters(typeParameterCount, validator)
            val typeParameters = (0 until typeParameterCount).map {
                TypeParameterDescriptorImpl.createWithDefaultBound(
                    fakeFunction,
                    Annotations.EMPTY,
                    false,
                    Variance.INVARIANT,
                    Name.identifier(parameterNames[it]),
                    it,
                    ktFileToEdit.getResolutionFacade().frontendService()
                )
            }

            return fakeFunction.initialize(
                null, null, emptyList(), typeParameters, Collections.emptyList(), null,
                null, DescriptorVisibilities.INTERNAL
            )
        }

        private fun renderTypeCandidates(
            typeInfo: TypeInfo,
            typeParameterNameMap: Map<TypeParameterDescriptor, String>,
            fakeFunction: FunctionDescriptor?
        ) {
            typeCandidates[typeInfo]?.forEach { it.render(typeParameterNameMap, fakeFunction) }
        }

        private fun isInsideInnerOrLocalClass(): Boolean {
            val classOrObject = containingElement.getNonStrictParentOfType<KtClassOrObject>()
            return classOrObject is KtClass && (classOrObject.isInner() || classOrObject.isLocal)
        }

        private fun createDeclarationSkeleton(): KtNamedDeclaration {
            val assignmentToReplace =
                if (containingElement is KtBlockExpression && (callableInfo as? PropertyInfo)?.writable == true) {
                    config.originalElement as KtBinaryExpression
                } else null
            val pointerOfAssignmentToReplace = assignmentToReplace?.createSmartPointer()

            val ownerTypeString = if (config.isExtension) {
                val renderedType = receiverTypeCandidate!!.renderedTypes.first()
                val isFunctionType = receiverTypeCandidate.theType.constructor.declarationDescriptor is FunctionClassDescriptor
                if (isFunctionType) "($renderedType)." else "$renderedType."
            } else ""

            val classKind = (callableInfo as? ClassWithPrimaryConstructorInfo)?.classInfo?.kind

            fun renderParamList(): String {
                val prefix = if (classKind == ClassKind.ANNOTATION_CLASS) "val " else ""
                val list = callableInfo.parameterInfos.indices.joinToString(", ") { i -> "${prefix}p$i: Any" }
                return if (callableInfo.parameterInfos.isNotEmpty()
                    || callableInfo.kind == CallableKind.FUNCTION
                    || callableInfo.kind == CallableKind.CONSTRUCTOR
                ) "($list)" else list
            }

            val paramList = when (callableInfo.kind) {
                CallableKind.FUNCTION, CallableKind.CLASS_WITH_PRIMARY_CONSTRUCTOR, CallableKind.CONSTRUCTOR ->
                    renderParamList()
                CallableKind.PROPERTY -> ""
            }
            val returnTypeString = if (skipReturnType || assignmentToReplace != null) "" else ": Any"
            val header = "$ownerTypeString${callableInfo.name.quoteIfNeeded()}$paramList$returnTypeString"

            val psiFactory = KtPsiFactory(config.currentFile.project)

            val modifiers = buildString {
                val modifierList = callableInfo.modifierList?.copied() ?: psiFactory.createEmptyModifierList()
                val visibilityKeyword = modifierList.visibilityModifierType()
                if (visibilityKeyword == null) {
                    CreateFromUsageUtil.visibilityModifierToString(CreateFromUsageUtil.computeDefaultVisibilityAsJvmModifier(
                        containingElement,
                        callableInfo.isAbstract,
                        config.isExtension,
                        (callableInfo.kind == CallableKind.CONSTRUCTOR),
                        config.originalElement
                    ))?.let {
                        append(it)
                        append(" ")
                    }
                }

                // TODO: Get rid of isAbstract
                if (callableInfo.isAbstract
                    && containingElement is KtClass
                    && !containingElement.isInterface()
                    && !modifierList.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                ) {
                    modifierList.appendModifier(KtTokens.ABSTRACT_KEYWORD)
                }

                val text = modifierList.normalize().text
                if (text.isNotEmpty()) {
                    append("$text ")
                }
            }

            val isExpectClassMember by lazy {
                containingElement is KtClassOrObject && containingElement.resolveToDescriptorIfAny()?.isExpect ?: false
            }

            val declaration: KtNamedDeclaration = when (callableInfo.kind) {
                CallableKind.FUNCTION, CallableKind.CONSTRUCTOR -> {
                    val body = when {
                        callableInfo is ConstructorInfo -> if (callableInfo.withBody) "{\n\n}" else ""
                        callableInfo.isAbstract -> ""
                        containingElement is KtClass && containingElement.hasModifier(KtTokens.EXTERNAL_KEYWORD) -> ""
                        containingElement is KtObjectDeclaration && containingElement.hasModifier(KtTokens.EXTERNAL_KEYWORD) -> ""
                        containingElement is KtObjectDeclaration && containingElement.isCompanion() &&
                                containingElement.parents.match(KtClassBody::class, last = KtClass::class)
                                    ?.hasModifier(KtTokens.EXTERNAL_KEYWORD) == true -> ""
                        isExpectClassMember -> ""
                        else -> "{\n\n}"

                    }
                    @Suppress("USELESS_CAST") // KT-10755
                    when {
                        callableInfo is FunctionInfo -> {
                            val braces = if (isStartTemplate) "<>" else ""
                            psiFactory.createFunction("${modifiers}fun$braces $header $body") as KtNamedDeclaration
                        }
                        (callableInfo as ConstructorInfo).isPrimary -> {
                            val constructorText = if (modifiers.isNotEmpty()) "${modifiers}constructor$paramList" else paramList
                            psiFactory.createPrimaryConstructor(constructorText) as KtNamedDeclaration
                        }
                        else -> psiFactory.createSecondaryConstructor("${modifiers}constructor$paramList $body") as KtNamedDeclaration
                    }
                }
                CallableKind.CLASS_WITH_PRIMARY_CONSTRUCTOR -> {
                    val classWithPrimaryConstructorInfo = callableInfo as ClassWithPrimaryConstructorInfo
                    val classInfo = classWithPrimaryConstructorInfo.classInfo

                    CreateClassUtil.createClassDeclaration(config.currentFile.project,
                                                           paramList,
                                                           returnTypeString,
                                                           classInfo.kind,
                                                           classInfo.name,
                                                           classInfo.applicableParents,
                                                           classInfo.open,
                                                           classInfo.inner, isInsideInnerOrLocalClass(), classWithPrimaryConstructorInfo.primaryConstructorVisibility?.name
                    )
                }
                CallableKind.PROPERTY -> {
                    val isVar = (callableInfo as PropertyInfo).writable
                    val const = if (callableInfo.isConst) "const " else ""
                    val valVar = if (isVar) "var" else "val"
                    val braces = if (isStartTemplate) "<>" else ""
                    val accessors = if (config.isExtension && !isExpectClassMember) {
                        buildString {
                            append("\nget() {}")
                            if (isVar) {
                                append("\nset(value) {}")
                            }
                        }
                    } else ""
                    psiFactory.createProperty("$modifiers$const$valVar$braces $header$accessors")
                }
            }

            callableInfo.annotations.forEach { declaration.addAnnotationEntry(it) }

            val newInitializer = pointerOfAssignmentToReplace?.element
            if (newInitializer != null) {
                (declaration as KtProperty).initializer = newInitializer.right
                return newInitializer.replace(declaration) as KtCallableDeclaration
            }

            val container = if (containingElement is KtClass && callableInfo.isForCompanion) {
                containingElement.getOrCreateCompanionObject()
            } else containingElement

            val declarationInPlace =  if (elementToReplace!=null) {
                elementToReplace?.replace(declaration) as KtNamedDeclaration
            } else {
                CreateFromUsageUtil.placeDeclarationInContainer(declaration, container, config.originalElement, ktFileToEdit)
            }

            if (declarationInPlace is KtSecondaryConstructor) {
                val containingClass = declarationInPlace.containingClassOrObject!!
                val primaryConstructorParameters = containingClass.primaryConstructorParameters
                if (primaryConstructorParameters.isNotEmpty()) {
                    declarationInPlace.replaceImplicitDelegationCallWithExplicit(true)
                } else if ((receiverClassDescriptor as ClassDescriptor).getSuperClassOrAny().constructors
                        .all { it.valueParameters.isNotEmpty() }
                ) {
                    declarationInPlace.replaceImplicitDelegationCallWithExplicit(false)
                }
                if (declarationInPlace.valueParameters.size > primaryConstructorParameters.size) {
                    val hasCompatibleTypes = primaryConstructorParameters.zip(callableInfo.parameterInfos).all { (primary, secondary) ->
                        val primaryType = currentFileContext[BindingContext.TYPE, primary.typeReference] ?: return@all false
                        val secondaryType = computeTypeCandidates(secondary.typeInfo).firstOrNull()?.theType ?: return@all false
                        secondaryType.isSubtypeOf(primaryType)
                    }
                    if (hasCompatibleTypes) {
                        val delegationCallArgumentList = declarationInPlace.getDelegationCall().valueArgumentList
                        primaryConstructorParameters.forEach {
                            val name = it.name
                            if (name != null) delegationCallArgumentList?.addArgument(psiFactory.createArgument(name))
                        }
                    }
                }
            }

            return declarationInPlace
        }

        private fun getTypeParameterRenames(scope: HierarchicalScope): Map<TypeParameterDescriptor, String> {
            val allTypeParametersNotInScope = LinkedHashSet<TypeParameterDescriptor>()

            mandatoryTypeParametersAsCandidates.asSequence()
                .plus(callableInfo.parameterInfos.asSequence().flatMap { typeCandidates[it.typeInfo]!!.asSequence() })
                .flatMap { it.typeParameters.asSequence() }
                .toCollection(allTypeParametersNotInScope)

            if (!skipReturnType) {
                computeTypeCandidates(callableInfo.returnTypeInfo).flatMapTo(allTypeParametersNotInScope) {
                    it.typeParameters.asSequence()
                }
            }

            val validator = CollectingNameValidator { scope.findClassifier(Name.identifier(it), NoLookupLocation.FROM_IDE) == null }
            val typeParameterNames = allTypeParametersNotInScope.map {
                KotlinNameSuggester.suggestNameByName(it.name.asString(), validator)
            }

            return allTypeParametersNotInScope.zip(typeParameterNames).toMap()
        }

        private fun setupTypeReferencesForShortening(
            declaration: KtNamedDeclaration,
            parameterTypeExpressions: List<TypeExpression>
        ) {
            if (config.isExtension) {
                val receiverTypeText = receiverTypeCandidate!!.theType.renderLong(typeParameterNameMap).first()
                val replacingTypeRef = KtPsiFactory(declaration.project).createType(receiverTypeText)
                (declaration as KtCallableDeclaration).setReceiverTypeReference(replacingTypeRef)!!
            }

            val returnTypeRefs = declaration.getReturnTypeReferences()
            if (returnTypeRefs.isNotEmpty()) {
                val returnType = typeCandidates[callableInfo.returnTypeInfo]!!.getTypeByRenderedType(
                    returnTypeRefs.map { it.text }
                )
                if (returnType != null) {
                    // user selected a given type
                    replaceWithLongerName(returnTypeRefs, returnType)
                }
            }

            val valueParameters = declaration.getValueParameters()
            val parameterIndicesToShorten = ArrayList<Int>()
            assert(valueParameters.size == parameterTypeExpressions.size)
            for ((i, parameter) in valueParameters.asSequence().withIndex()) {
                val parameterTypeRef = parameter.typeReference
                if (parameterTypeRef != null) {
                    val parameterType = parameterTypeExpressions[i].typeCandidates.getTypeByRenderedType(
                        listOf(parameterTypeRef.text)
                    )
                    if (parameterType != null) {
                        replaceWithLongerName(listOf(parameterTypeRef), parameterType)
                        parameterIndicesToShorten.add(i)
                    }
                }
            }
        }

        private fun postprocessDeclaration(declaration: KtNamedDeclaration) {
            if (callableInfo is PropertyInfo && callableInfo.isLateinitPreferred) {
                if (declaration.containingClassOrObject == null) return
                val propertyDescriptor = declaration.resolveToDescriptorIfAny() as? PropertyDescriptor ?: return
                val returnType = propertyDescriptor.returnType ?: return
                if (TypeUtils.isNullableType(returnType) || KotlinBuiltIns.isPrimitiveType(returnType)) return
                declaration.addModifier(KtTokens.LATEINIT_KEYWORD)
            }

            if (callableInfo.isAbstract) {
                val containingClass = declaration.containingClassOrObject
                if (containingClass is KtClass && containingClass.isInterface()) {
                    declaration.removeModifier(KtTokens.ABSTRACT_KEYWORD)
                }
            }
        }

        private fun setupDeclarationBody(func: KtDeclarationWithBody) {
            if (func !is KtNamedFunction && func !is KtPropertyAccessor) return
            if (skipReturnType && callableInfo is FunctionInfo && callableInfo.preferEmptyBody) return
            val oldBody = func.bodyExpression ?: return
            val bodyText = getFunctionBodyTextFromTemplate(
                func.project,
                TemplateKind.FUNCTION,
                callableInfo.name.ifEmpty { null },
                if (skipReturnType) "Unit" else (func as? KtFunction)?.typeReference?.text ?: "",
                receiverClassDescriptor?.importableFqName ?: receiverClassDescriptor?.name?.let { FqName.topLevel(it) }
            )
            oldBody.replace(KtPsiFactory(func.project).createBlock(bodyText))
        }

        private fun setupCallTypeArguments(callElement: KtCallElement, typeParameters: List<TypeParameterDescriptor>) {
            val oldTypeArgumentList = callElement.typeArgumentList ?: return
            val renderedTypeArgs = typeParameters.map { typeParameter ->
                val type = substitutions.first { it.byType.constructor.declarationDescriptor == typeParameter }.forType
                IdeDescriptorRenderers.SOURCE_CODE.renderType(type)
            }
            if (renderedTypeArgs.isEmpty()) {
                oldTypeArgumentList.delete()
            } else {
                val psiFactory = KtPsiFactory(callElement.project)
                oldTypeArgumentList.replace(psiFactory.createTypeArguments(renderedTypeArgs.joinToString(", ", "<", ">")))
                elementsToShorten.add(callElement.typeArgumentList!!)
            }
        }

        private fun setupReturnTypeTemplate(builder: TemplateBuilder, declaration: KtNamedDeclaration): TypeExpression? {
            val candidates = typeCandidates[callableInfo.returnTypeInfo]!!
            if (candidates.isEmpty()) return null

            val elementToReplace: KtElement?
            val expression: TypeExpression = when (declaration) {
                is KtCallableDeclaration -> {
                    elementToReplace = declaration.typeReference
                    TypeExpression.ForTypeReference(candidates)
                }
                is KtClassOrObject -> {
                    elementToReplace = declaration.superTypeListEntries.firstOrNull()
                    TypeExpression.ForDelegationSpecifier(candidates)
                }
                else -> throw KotlinExceptionWithAttachments("Unexpected declaration kind: ${declaration::class.java}")
                    .withPsiAttachment("declaration", declaration)
            }
            if (elementToReplace == null) return null

            if (candidates.size == 1) {
                val resultType = (expression.calculateResult(null) as TextResult).text
                if (isStartTemplate) {
                    builder.replaceElement(elementToReplace, resultType)
                } else {
                    elementToReplace.replace(KtPsiFactory(declaration.project).createType(resultType))
                }
                return null
            }

            builder.replaceElement(elementToReplace, expression)
            return expression
        }

        private fun setupValVarTemplate(builder: TemplateBuilder, property: KtProperty) {
            if (!(callableInfo as PropertyInfo).writable) {
                builder.replaceElement(property.valOrVarKeyword, ValVarExpression)
            }
        }

        private fun setupTypeParameterListTemplate(
            builder: TemplateBuilderImpl,
            declaration: KtNamedDeclaration
        ): TypeParameterListExpression? {
            when (declaration) {
                is KtObjectDeclaration -> return null
                !is KtTypeParameterListOwner -> {
                    throw KotlinExceptionWithAttachments("Unexpected declaration kind: ${declaration::class.java}")
                        .withPsiAttachment("declaration", declaration)
                }
            }

            val typeParameterList = (declaration as KtTypeParameterListOwner).typeParameterList ?: return null

            val typeParameterMap = HashMap<String, List<RenderedTypeParameter>>()

            val mandatoryTypeParameters = ArrayList<RenderedTypeParameter>()
            mandatoryTypeParametersAsCandidates.flatMapTo(mandatoryTypeParameters) { it.renderedTypeParameters!!.asSequence() }

            callableInfo.parameterInfos.asSequence()
                .flatMap { typeCandidates[it.typeInfo]!!.asSequence() }
                .forEach { typeParameterMap[it.renderedTypes.first()] = it.renderedTypeParameters!! }

            if (declaration.getReturnTypeReference() != null) {
                typeCandidates[callableInfo.returnTypeInfo]!!.forEach {
                    typeParameterMap[it.renderedTypes.first()] = it.renderedTypeParameters!!
                }
            }

            val expression = TypeParameterListExpression(
                mandatoryTypeParameters,
                typeParameterMap,
                callableInfo.kind != CallableKind.CLASS_WITH_PRIMARY_CONSTRUCTOR
            )
            val leftSpace = typeParameterList.prevSibling as? PsiWhiteSpace
            val rangeStart = leftSpace?.startOffset ?: typeParameterList.startOffset
            val offset = typeParameterList.startOffset
            val range = UnfairTextRange(rangeStart - offset, typeParameterList.endOffset - offset)
            if (isStartTemplate || typeParameterMap.isEmpty() || typeParameterMap.size > 1) {
                builder.replaceElement(typeParameterList, range, "TYPE_PARAMETER_LIST", expression, false)
            }
            return expression
        }

        private fun setupParameterTypeTemplates(builder: TemplateBuilder, parameterList: List<KtParameter>): List<TypeExpression> {
            assert(parameterList.size == callableInfo.parameterInfos.size)

            val typeParameters = ArrayList<TypeExpression>()
            for ((parameter, ktParameter) in callableInfo.parameterInfos.zip(parameterList)) {
                val parameterTypeExpression = TypeExpression.ForTypeReference(typeCandidates[parameter.typeInfo]!!)
                val parameterTypeRef = ktParameter.typeReference!!
                builder.replaceElement(parameterTypeRef, parameterTypeExpression)

                // add parameter name to the template
                val possibleNamesFromExpression = parameter.typeInfo.getPossibleNamesFromExpression(currentFileContext)
                val possibleNames = arrayOf(*parameter.nameSuggestions.toTypedArray(), *possibleNamesFromExpression)

                // figure out suggested names for each type option
                val parameterTypeToNamesMap = HashMap<String, Array<String>>()
                typeCandidates[parameter.typeInfo]!!.forEach { typeCandidate ->
                    val suggestedNames = Fe10KotlinNameSuggester.suggestNamesByType(typeCandidate.theType, { true })
                    parameterTypeToNamesMap[typeCandidate.renderedTypes.first()] = suggestedNames.toTypedArray()
                }

                // add expression to builder
                val parameterNameExpression = ParameterNameExpression(possibleNames, parameterTypeToNamesMap)
                val parameterNameIdentifier = ktParameter.nameIdentifier!!
                builder.replaceElement(parameterNameIdentifier, parameterNameExpression)

                typeParameters.add(parameterTypeExpression)
            }
            return typeParameters
        }

        private fun replaceWithLongerName(typeRefs: List<KtTypeReference>, theType: KotlinType) {
            val psiFactory = KtPsiFactory(ktFileToEdit.project)
            val fullyQualifiedReceiverTypeRefs = theType.renderLong(typeParameterNameMap).map { psiFactory.createType(it) }
            (typeRefs zip fullyQualifiedReceiverTypeRefs).forEach { (shortRef, longRef) -> shortRef.replace(longRef) }
        }

        private fun setupEditor(declaration: KtNamedDeclaration, setupEditor: Boolean) {
            if (declaration is KtProperty && !declaration.hasInitializer() && containingElement is KtBlockExpression) {
                val psiFactory = KtPsiFactory(declaration.project)

                val defaultValueType = typeCandidates[callableInfo.returnTypeInfo]!!.firstOrNull()?.theType
                val defaultValue = defaultValueType?.getDefaultInitializer() ?: "null"
                val initializer = declaration.setInitializer(psiFactory.createExpression(defaultValue))!!
                val range = initializer.textRange
                containingFileEditor.selectionModel.setSelection(range.startOffset, range.endOffset)
                containingFileEditor.caretModel.moveToOffset(range.endOffset)
                return
            }
            if (declaration is KtSecondaryConstructor && !declaration.hasImplicitDelegationCall()) {
                containingFileEditor.caretModel.moveToOffset(declaration.getDelegationCall().valueArgumentList!!.startOffset + 1)
                return
            }
            if (setupEditor) {
                setupEditorSelection(containingFileEditor, declaration)
            }
        }

        // build templates
        fun buildAndRunTemplate(onFinish: () -> Unit) {
            val declarationSkeleton = createDeclarationSkeleton()
            val project = declarationSkeleton.project
            val declarationPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(declarationSkeleton)

            // build templates
            val documentManager = PsiDocumentManager.getInstance(project)
            val document = containingFileEditor.document
            documentManager.commitDocument(document)
            documentManager.doPostponedOperationsAndUnblockDocument(document)

            val caretModel = containingFileEditor.caretModel
            val injectedOffsetOrZero = if (ktFileToEdit.virtualFile is VirtualFileWindow) {
                InjectedLanguageManager.getInstance(ktFileToEdit.project).injectedToHost(ktFileToEdit, ktFileToEdit.textOffset)
            } else 0
            caretModel.moveToOffset(ktFileToEdit.node.startOffset + injectedOffsetOrZero)

            val declaration = declarationPointer.element ?: return

            val declarationMarker = document.createRangeMarker(declaration.textRange)

            val builder = TemplateBuilderImpl(ktFileToEdit)
            builder.setScrollToTemplate(isStartTemplate)
            if (declaration is KtProperty) {
                if (isStartTemplate) {
                    setupValVarTemplate(builder, declaration)
                }
            }
            if (!skipReturnType) {
                setupReturnTypeTemplate(builder, declaration)
            }

            val parameterTypeExpressions = setupParameterTypeTemplates(builder, declaration.getValueParameters())

            // add a segment for the parameter list
            // Note: because TemplateBuilderImpl does not have a replaceElement overload that takes in both a TextRange and alwaysStopAt, we
            // need to create the segment first and then hack the Expression into the template later. We use this template to update the type
            // parameter list as the user makes selections in the parameter types, and we need alwaysStopAt to be false so the user can't tab to
            // it.
            val expression = setupTypeParameterListTemplate(builder, declaration)

            documentManager.doPostponedOperationsAndUnblockDocument(document)

            // the template built by TemplateBuilderImpl is ordered by element position, but we want types to be first, so hack it
            val templateImpl = builder.buildInlineTemplate() as TemplateImpl
            val variables = templateImpl.variables!!

            if (isStartTemplate && variables.isNotEmpty()) {
                val typeParametersVar = if (expression != null) variables.removeAt(0) else null
                for (i in callableInfo.parameterInfos.indices) {
                    Collections.swap(variables, i * 2, i * 2 + 1)
                }
                typeParametersVar?.let { variables.add(it) }
            }

            // TODO: Disabled shortening names because it causes some tests fail. Refactor code to use automatic reference shortening
            templateImpl.isToShortenLongNames = false

            // run the template
            val templateEditAdapter = object : TemplateEditingAdapter() {
                private fun finishTemplate(brokenOff: Boolean) {
                    try {
                        documentManager.commitDocument(document)

                        dialogWithEditor?.close(DialogWrapper.OK_EXIT_CODE)
                        if (brokenOff && !isUnitTestMode()) return

                        // file templates
                        val newDeclarationPointer = PsiTreeUtil.findElementOfClassAtOffset(
                            ktFileToEdit,
                            declarationMarker.startOffset,
                            declaration::class.java,
                            false
                        )?.createSmartPointer() ?: return

                        if (IntentionPreviewUtils.isPreviewElement(config.currentFile)) return

                        WriteCommandAction.writeCommandAction(project).run<Throwable> {
                            val newDeclaration = newDeclarationPointer.element ?: return@run
                            postprocessDeclaration(newDeclaration)

                            // file templates
                            if (newDeclaration is KtNamedFunction || newDeclaration is KtSecondaryConstructor) {
                                setupDeclarationBody(newDeclaration as KtFunction)
                            }

                            if (newDeclaration is KtProperty) {
                                newDeclaration.getter?.let { setupDeclarationBody(it) }

                                if (callableInfo is PropertyInfo && callableInfo.initializer != null) {
                                    newDeclaration.initializer = callableInfo.initializer
                                }
                            }

                            val callElement = config.originalElement as? KtCallElement
                            if (callElement != null) {
                                setupCallTypeArguments(callElement, expression?.currentTypeParameters ?: Collections.emptyList())
                            }

                            CodeStyleManager.getInstance(project).reformat(newDeclaration)

                            // change short type names to fully qualified ones (to be shortened below)
                            if (newDeclaration.getValueParameters().size == parameterTypeExpressions.size) {
                                setupTypeReferencesForShortening(newDeclaration, parameterTypeExpressions)
                            }
                            val needStatic = when (callableInfo) {
                                is ClassWithPrimaryConstructorInfo -> with(callableInfo.classInfo) {
                                    !inner && kind != ClassKind.ENUM_ENTRY && kind != ClassKind.ENUM_CLASS
                                }

                                else -> callableInfo.receiverTypeInfo.staticContextRequired
                            }
                            val isExtension = config.isExtension || receiverClassDescriptor !is JavaClassDescriptor
                            val targetClass = if (receiverClassDescriptor is DeclarationDescriptor) DescriptorToSourceUtils.getSourceFromDescriptor(receiverClassDescriptor) as? PsiClass else null
                            if (targetClass == null || !transformToJavaMemberIfApplicable(
                                    newDeclaration,
                                    config.currentFile.packageFqName,
                                    isExtension,
                                    needStatic,
                                    targetClass
                                )) {
                                elementsToShorten.add(newDeclaration)
                                setupEditor(newDeclaration, isStartTemplate)
                            }
                        }
                    } finally {
                        declarationMarker.dispose()
                        finished = true
                        onFinish()
                    }
                }

                override fun templateCancelled(template: Template?) {
                    finishTemplate(true)
                }

                override fun templateFinished(template: Template, brokenOff: Boolean) {
                    finishTemplate(brokenOff)
                }
            }

            //silently complete template if no variables defined
            if (!isStartTemplate && variables.isEmpty()) {
                templateEditAdapter.templateFinished(templateImpl,false)
                return
            }

            TemplateManager.getInstance(project).startTemplate(containingFileEditor, templateImpl, templateEditAdapter)
        }

        fun showDialogIfNeeded() {
            if (!isUnitTestMode() && dialogWithEditor != null && !finished) {
                dialogWithEditor.show()
            }
        }
    }
}

@Deprecated(
    message = "Use org.jetbrains.kotlin.idea.base.psi.KotlinPsiUtils.getReturnTypeReference instead",
    ReplaceWith("getReturnTypeReference", "org.jetbrains.kotlin.idea.base.psi.KotlinPsiUtils")
)
fun KtNamedDeclaration.getReturnTypeReference(): KtTypeReference? = getReturnTypeReference()

fun CallableBuilderConfiguration.createBuilder(): CallableBuilder = CallableBuilder(this)
