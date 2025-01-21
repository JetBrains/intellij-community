// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.builtins.isFunctionOrSuspendFunctionType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.extensions.DeclarationAttributeAltererExtension
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.*
import org.jetbrains.kotlin.idea.base.psi.addTypeParameter
import org.jetbrains.kotlin.idea.base.psi.appendDeclaration
import org.jetbrains.kotlin.idea.base.psi.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.base.psi.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.idea.base.psi.setDefaultValue
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.refactoring.addElement
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.idea.refactoring.isComplexCallWithLambdaArgument
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.hasJvmFieldAnnotation
import org.jetbrains.kotlin.idea.util.isExpectDeclaration
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.addRemoveModifier.MODIFIERS_ORDER
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.isFakeElement
import org.jetbrains.kotlin.resolve.checkers.ExplicitApiDeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.explicitApiEnabled
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.sam.SamConversionOracle
import org.jetbrains.kotlin.resolve.sam.SamConversionResolver
import org.jetbrains.kotlin.resolve.sam.getFunctionTypeForPossibleSamType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun KtLambdaArgument.moveInsideParentheses(bindingContext: BindingContext): KtCallExpression {
    val ktExpression = this.getArgumentExpression()
        ?: throw KotlinExceptionWithAttachments("no argument expression for $this")
            .withPsiAttachment("lambdaExpression", this)
    return moveInsideParenthesesAndReplaceWith(ktExpression, bindingContext)
}

/**
 * Moves the lambda argument inside parentheses and replaces it with the specified replacement expression.
 * If the lambda argument should be named, it retrieves the lambda argument name from the binding context.
 *
 * @param replacement The replacement expression to be used.
 * @param bindingContext The binding context used to retrieve the lambda argument name if necessary.
 * @return The modified `KtCallExpression` with the lambda argument moved inside parentheses and replaced with
 * the specified replacement expression.
 */
fun KtLambdaArgument.moveInsideParenthesesAndReplaceWith(
    replacement: KtExpression,
    bindingContext: BindingContext
): KtCallExpression {
    val lambdaArgumentName = if (shouldLambdaParameterBeNamed(this)) {
        this.getLambdaArgumentName(bindingContext)
    } else null
    return this.moveInsideParenthesesAndReplaceWith(replacement, lambdaArgumentName)
}

fun KtLambdaArgument.getLambdaArgumentName(bindingContext: BindingContext): Name? {
    val callExpression = parent as KtCallExpression
    val resolvedCall = callExpression.getResolvedCall(bindingContext)
    return (resolvedCall?.getArgumentMapping(this) as? ArgumentMatch)?.valueParameter?.name
}

fun KtLambdaExpression.moveFunctionLiteralOutsideParenthesesIfPossible() {
    val valueArgument = parentOfType<KtValueArgument>()?.takeIf {
        KtPsiUtil.deparenthesize(it.getArgumentExpression()) == this
    } ?: return
    val valueArgumentList = valueArgument.parent as? KtValueArgumentList ?: return
    val call = valueArgumentList.parent as? KtCallExpression ?: return
    if (call.canMoveLambdaOutsideParentheses()) {
        call.moveFunctionLiteralOutsideParentheses()
    }
}

@OptIn(FrontendInternals::class)
fun KtCallExpression.canMoveLambdaOutsideParentheses(skipComplexCalls: Boolean = true): Boolean {
    if (skipComplexCalls && isComplexCallWithLambdaArgument()) return false

    if (getStrictParentOfType<KtDelegatedSuperTypeEntry>() != null) return false
    val lastLambdaExpression = getLastLambdaExpression() ?: return false
    if (lastLambdaExpression.parentLabeledExpression()?.parentLabeledExpression() != null) return false

    val callee = calleeExpression
    if (callee !is KtNameReferenceExpression) return true

    val resolutionFacade = getResolutionFacade()
    val samConversionTransformer = resolutionFacade.frontendService<SamConversionResolver>()
    val samConversionOracle = resolutionFacade.frontendService<SamConversionOracle>()
    val languageVersionSettings = resolutionFacade.languageVersionSettings
    val newInferenceEnabled = languageVersionSettings.supportsFeature(LanguageFeature.NewInference)

    val bindingContext = safeAnalyzeNonSourceRootCode(resolutionFacade, BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
    if (bindingContext.diagnostics.forElement(lastLambdaExpression).none { it.severity == Severity.ERROR }) {
        val resolvedCall = getResolvedCall(bindingContext)
        if (resolvedCall != null) {
            val parameter = resolvedCall.getParameterForArgument(valueArguments.last()) ?: return false
            val functionDescriptor = resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return false
            if (parameter != functionDescriptor.valueParameters.lastOrNull()) return false
            return parameter.type.allowsMoveOutsideParentheses(samConversionTransformer, samConversionOracle, newInferenceEnabled)
        }
    }

    val targets = bindingContext[BindingContext.REFERENCE_TARGET, callee]?.let { listOf(it) }
        ?: bindingContext[BindingContext.AMBIGUOUS_REFERENCE_TARGET, callee]
        ?: listOf()
    val candidates = targets.filterIsInstance<FunctionDescriptor>()

    val lambdaArgumentCount = valueArguments.count { it.getArgumentExpression()?.unpackFunctionLiteral() != null }
    val referenceArgumentCount = valueArguments.count { it.getArgumentExpression() is KtCallableReferenceExpression }

    // if there are functions among candidates but none of them have last function parameter then not show the intention
    val areAllCandidatesWithoutLastFunctionParameter = candidates.none {
        it.allowsMoveOfLastParameterOutsideParentheses(
            lambdaArgumentCount + referenceArgumentCount,
            samConversionTransformer,
            samConversionOracle,
            newInferenceEnabled
        )
    }

    return candidates.isEmpty() || !areAllCandidatesWithoutLastFunctionParameter
}

private fun KtExpression.parentLabeledExpression(): KtLabeledExpression? {
    return getStrictParentOfType<KtLabeledExpression>()?.takeIf { it.baseExpression == this }
}

private fun KotlinType.allowsMoveOutsideParentheses(
    samConversionTransformer: SamConversionResolver,
    samConversionOracle: SamConversionOracle,
    newInferenceEnabled: Boolean
): Boolean {
    // Fast-path
    if (isFunctionOrSuspendFunctionType || isTypeParameter()) return true

    // Also check if it can be SAM-converted
    // Note that it is not necessary in OI, where we provide synthetic candidate descriptors with already
    // converted types, but in NI it is performed by conversions, so we check it explicitly
    // Also note that 'newInferenceEnabled' is essentially a micro-optimization, as there are no
    // harm in just calling 'samConversionTransformer' on all candidates.
    return newInferenceEnabled && samConversionTransformer.getFunctionTypeForPossibleSamType(this.unwrap(), samConversionOracle) != null
}

private fun FunctionDescriptor.allowsMoveOfLastParameterOutsideParentheses(
    lambdaAndCallableReferencesInOriginalCallCount: Int,
    samConversionTransformer: SamConversionResolver,
    samConversionOracle: SamConversionOracle,
    newInferenceEnabled: Boolean
): Boolean {
    val params = valueParameters
    val lastParamType = params.lastOrNull()?.type ?: return false

    if (!lastParamType.allowsMoveOutsideParentheses(samConversionTransformer, samConversionOracle, newInferenceEnabled)) return false

    val movableParametersOfCandidateCount = params.count {
        it.type.allowsMoveOutsideParentheses(samConversionTransformer, samConversionOracle, newInferenceEnabled)
    }
    return movableParametersOfCandidateCount == lambdaAndCallableReferencesInOriginalCallCount
}

@Deprecated("Use addElement directly", ReplaceWith("addElement", "org.jetbrains.kotlin.idea.refactoring.addElement"))
fun KtBlockExpression.appendElement(element: KtElement, addNewLine: Boolean = false): KtElement {
   return addElement(element, addNewLine)
}

//TODO: git rid of this method
fun PsiElement.deleteElementAndCleanParent() {
    val parent = parent

    deleteElementWithDelimiters(this)
    deleteChildlessElement(parent, this::class.java)
}

// Delete element if it doesn't contain children of a given type
private fun <T : PsiElement> deleteChildlessElement(element: PsiElement, childClass: Class<T>) {
    if (PsiTreeUtil.getChildrenOfType(element, childClass) == null) {
        element.delete()
    }
}

// Delete given element and all the elements separating it from the neighboring elements of the same class
private fun deleteElementWithDelimiters(element: PsiElement) {
    val paramBefore = PsiTreeUtil.getPrevSiblingOfType(element, element.javaClass)

    val from: PsiElement
    val to: PsiElement
    if (paramBefore != null) {
        from = paramBefore.nextSibling
        to = element
    } else {
        val paramAfter = PsiTreeUtil.getNextSiblingOfType(element, element.javaClass)

        from = element
        to = if (paramAfter != null) paramAfter.prevSibling else element
    }

    val parent = element.parent

    parent.deleteChildRange(from, to)
}

@ApiStatus.ScheduledForRemoval
@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.psi.KotlinPsiModificationUtils' instead",
    ReplaceWith("this.getOrCreateCompanionObject()", "org.jetbrains.kotlin.idea.base.psi.getOrCreateCompanionObject")
)
fun KtClass.getOrCreateCompanionObject(): KtObjectDeclaration = getOrCreateCompanionObject()

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.psi.KotlinPsiModificationUtils' instead",
    ReplaceWith("this.appendDeclaration(declaration)", "org.jetbrains.kotlin.idea.base.psi.appendDeclaration")
)
inline fun <reified T : KtDeclaration> KtClass.appendDeclaration(declaration: T): T  = appendDeclaration(declaration)

fun KtDeclaration.toDescriptor(): DeclarationDescriptor? {
    if (this is KtScriptInitializer) {
        return null
    }

    return resolveToDescriptorIfAny()
}

fun KtModifierListOwner.setVisibility(visibilityModifier: KtModifierKeywordToken, addImplicitVisibilityModifier: Boolean = false) {
    if (this is KtDeclaration && !addImplicitVisibilityModifier) {
        val defaultVisibilityKeyword = implicitVisibility()

        if (visibilityModifier == defaultVisibilityKeyword) {
            // Fake elements do not have ModuleInfo and languageVersionSettings because they can't be analysed
            // Effectively, this leads to J2K not respecting explicit api mode, but this case seems to be rare anyway.
            val explicitVisibilityRequired = !this.isFakeElement &&
                    this.languageVersionSettings.explicitApiEnabled &&
                    this.resolveToDescriptorIfAny()?.let { !ExplicitApiDeclarationChecker.explicitVisibilityIsNotRequired(it) } == true

            if (!explicitVisibilityRequired) {
                this.visibilityModifierType()?.let { removeModifier(it) }
                return
            }
        }
    }

    addModifier(visibilityModifier)
}

fun KtDeclaration.implicitVisibility(): KtModifierKeywordToken? {
    return when {
        this is KtPropertyAccessor && isSetter && property.hasModifier(KtTokens.OVERRIDE_KEYWORD) -> {
            property.resolveToDescriptorIfAny()
                ?.safeAs<PropertyDescriptor>()
                ?.overriddenDescriptors?.forEach {
                    val visibility = it.setter?.visibility?.toKeywordToken()
                    if (visibility != null) return visibility
                }

            KtTokens.DEFAULT_VISIBILITY_KEYWORD
        }

        this is KtConstructor<*> -> {
            // constructors cannot be declared in objects
            val klass = getContainingClassOrObject() as? KtClass ?: return KtTokens.DEFAULT_VISIBILITY_KEYWORD

            when {
                klass.isEnum() -> KtTokens.PRIVATE_KEYWORD
                klass.isSealed() ->
                    if (klass.languageVersionSettings.supportsFeature(LanguageFeature.SealedInterfaces)) KtTokens.PROTECTED_KEYWORD
                    else KtTokens.PRIVATE_KEYWORD

                else -> KtTokens.DEFAULT_VISIBILITY_KEYWORD
            }
        }

        hasModifier(KtTokens.OVERRIDE_KEYWORD) -> {
            resolveToDescriptorIfAny()?.safeAs<CallableMemberDescriptor>()
                ?.overriddenDescriptors
                ?.let { OverridingUtil.findMaxVisibility(it) }
                ?.toKeywordToken()
        }

        else -> KtTokens.DEFAULT_VISIBILITY_KEYWORD
    }
}

fun KtModifierListOwner.canBePrivate(): Boolean {
    if (modifierList?.hasModifier(KtTokens.ABSTRACT_KEYWORD) == true) return false
    if (this.isAnnotationClassPrimaryConstructor()) return false
    if (this is KtProperty && this.hasJvmFieldAnnotation()) return false

    if (this is KtDeclaration) {
        if (hasActualModifier() || isExpectDeclaration()) return false
        val containingClassOrObject = containingClassOrObject as? KtClass ?: return true
        if (containingClassOrObject.isAnnotation()) return false
        if (containingClassOrObject.isInterface() && !hasBody()) return false
    }

    return true
}

fun KtModifierListOwner.canBePublic(): Boolean = !isSealedClassConstructor()

fun KtModifierListOwner.canBeProtected(): Boolean {
    return when (val parent = if (this is KtPropertyAccessor) this.property.parent else this.parent) {
        is KtClassBody -> {
            val parentClass = parent.parent as? KtClass
            parentClass != null && !parentClass.isInterface() && !this.isFinalClassConstructor()
        }

        is KtParameterList -> parent.parent is KtPrimaryConstructor
        is KtClass -> !this.isAnnotationClassPrimaryConstructor() && !this.isFinalClassConstructor()
        else -> false
    }
}

fun KtModifierListOwner.canBeInternal(): Boolean {
    if (containingClass()?.isInterface() == true) {
        val objectDeclaration = getStrictParentOfType<KtObjectDeclaration>() ?: return false
        if (objectDeclaration.isCompanion() && hasJvmFieldAnnotation()) return false
    }

    return !isAnnotationClassPrimaryConstructor() && !isSealedClassConstructor()
}

private fun KtModifierListOwner.isAnnotationClassPrimaryConstructor(): Boolean =
    this is KtPrimaryConstructor && (this.parent as? KtClass)?.hasModifier(KtTokens.ANNOTATION_KEYWORD) ?: false

private fun KtModifierListOwner.isFinalClassConstructor(): Boolean {
    if (this !is KtConstructor<*>) return false
    val ktClass = getContainingClassOrObject().safeAs<KtClass>() ?: return false
    return ktClass.toDescriptor().safeAs<ClassDescriptor>()?.isFinalOrEnum ?: return false
}

private fun KtModifierListOwner.isSealedClassConstructor(): Boolean {
    if (this !is KtConstructor<*>) return false
    val ktClass = getContainingClassOrObject().safeAs<KtClass>() ?: return false
    return ktClass.isSealed()
}

fun KtClass.isInheritable(): Boolean {
    return when (getModalityFromDescriptor()) {
        KtTokens.ABSTRACT_KEYWORD, KtTokens.OPEN_KEYWORD, KtTokens.SEALED_KEYWORD -> true
        else -> false
    }
}

val KtParameter.isOverridable: Boolean
    get() = hasValOrVar() && !isEffectivelyFinal

val KtProperty.isOverridable: Boolean
    get() = !isTopLevel && !isEffectivelyFinal

private val KtDeclaration.isEffectivelyFinal: Boolean
    get() = hasModifier(KtTokens.FINAL_KEYWORD) ||
            !(hasModifier(KtTokens.OPEN_KEYWORD) || hasModifier(KtTokens.ABSTRACT_KEYWORD) || hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
                    (containingClassOrObject as? KtClass)?.isInterface() == true) ||
            containingClassOrObject?.isEffectivelyFinal == true

private val KtClassOrObject.isEffectivelyFinal: Boolean
    get() = this is KtObjectDeclaration ||
            this is KtClass && isEffectivelyFinal

private val KtClass.isEffectivelyFinal: Boolean
    get() = hasModifier(KtTokens.FINAL_KEYWORD) ||
            isData() ||
            !(isSealed() || hasModifier(KtTokens.OPEN_KEYWORD) || hasModifier(KtTokens.ABSTRACT_KEYWORD) || isInterface())

/**
 * copy-paste in K2: [org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupportFirImpl.isOverridable]
 */
fun KtDeclaration.isOverridable(): Boolean =
    !hasModifier(KtTokens.PRIVATE_KEYWORD) &&  // 'private' is incompatible with 'open'
            (parents.match(KtParameterList::class, KtPrimaryConstructor::class, last = KtClass::class)
                ?: parents.match(KtClassBody::class, last = KtClass::class))
                ?.let { it.isInheritable() || it.isEnum() } == true &&
            getModalityFromDescriptor() in setOf(KtTokens.ABSTRACT_KEYWORD, KtTokens.OPEN_KEYWORD)

fun KtDeclaration.getModalityFromDescriptor(descriptor: DeclarationDescriptor? = resolveToDescriptorIfAny()): KtModifierKeywordToken? {
    if (descriptor is MemberDescriptor) {
        return mapModality(descriptor.modality)
    }

    return null
}

fun KtDeclaration.implicitModality(): KtModifierKeywordToken {
    var predictedModality = predictImplicitModality()
    val bindingContext = safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
    val descriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, this] ?: return predictedModality
    val containingDescriptor = descriptor.containingDeclaration ?: return predictedModality

    val extensions = DeclarationAttributeAltererExtension.getInstances(this.project)
    for (extension in extensions) {
        val newModality = extension.refineDeclarationModality(
            this,
            descriptor as? ClassDescriptor,
            containingDescriptor,
            mapModalityToken(predictedModality),
            isImplicitModality = true
        )

        if (newModality != null) {
            predictedModality = mapModality(newModality)
        }
    }

    return predictedModality
}

fun mapModality(accurateModality: Modality): KtModifierKeywordToken = when (accurateModality) {
    Modality.FINAL -> KtTokens.FINAL_KEYWORD
    Modality.SEALED -> KtTokens.SEALED_KEYWORD
    Modality.OPEN -> KtTokens.OPEN_KEYWORD
    Modality.ABSTRACT -> KtTokens.ABSTRACT_KEYWORD
}

private fun mapModalityToken(modalityToken: IElementType): Modality = when (modalityToken) {
    KtTokens.FINAL_KEYWORD -> Modality.FINAL
    KtTokens.SEALED_KEYWORD -> Modality.SEALED
    KtTokens.OPEN_KEYWORD -> Modality.OPEN
    KtTokens.ABSTRACT_KEYWORD -> Modality.ABSTRACT
    else -> error("Unexpected modality keyword $modalityToken")
}

fun KtParameter.dropDefaultValue() {
    val from = equalsToken ?: return
    val to = defaultValue ?: from
    deleteChildRange(from, to)
}

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.psi.KotlinPsiModificationUtils' instead",
    ReplaceWith("this.addTypeParameter(typeParameter)", "org.jetbrains.kotlin.idea.base.psi.addTypeParameter")
)
fun KtTypeParameterListOwner.addTypeParameter(typeParameter: KtTypeParameter) = addTypeParameter(typeParameter)

fun KtNamedFunction.getOrCreateValueParameterList(): KtParameterList {
    valueParameterList?.let { return it }
    val parameterList = KtPsiFactory(project).createParameterList("()")
    val anchor = nameIdentifier ?: funKeyword!!
    return addAfter(parameterList, anchor) as KtParameterList
}

fun KtCallableDeclaration.setType(type: KotlinType, shortenReferences: Boolean = true) {
    if (type.isError) return
    setType(IdeDescriptorRenderers.SOURCE_CODE.renderType(type), shortenReferences)
}

fun KtCallableDeclaration.setType(typeString: String, shortenReferences: Boolean = true) {
    val typeReference = KtPsiFactory(project).createType(typeString)
    setTypeReference(typeReference)
    if (shortenReferences) {
        ShortenReferences.DEFAULT.process(getTypeReference()!!)
    }
}

fun KtCallableDeclaration.setReceiverType(type: KotlinType) {
    if (type.isError) return
    val typeReference = KtPsiFactory(project).createType(IdeDescriptorRenderers.SOURCE_CODE.renderType(type))
    setReceiverTypeReference(typeReference)
    ShortenReferences.DEFAULT.process(receiverTypeReference!!)
}

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.psi.KotlinPsiModificationUtils' instead",
    ReplaceWith("this.setDefaultValue(newDefaultValue)", "org.jetbrains.kotlin.idea.base.psi.setDefaultValue")
)
@ApiStatus.ScheduledForRemoval
fun KtParameter.setDefaultValue(newDefaultValue: KtExpression): PsiElement = setDefaultValue(newDefaultValue)

fun KtModifierList.appendModifier(modifier: KtModifierKeywordToken) {
    add(KtPsiFactory(project).createModifier(modifier))
}

fun KtModifierList.normalize(): KtModifierList {
    val psiFactory = KtPsiFactory(project)
    return psiFactory.createEmptyModifierList().also { newList ->
        val modifiers = SmartList<PsiElement>()
        allChildren.forEach {
            val elementType = it.node.elementType
            when {
                it is KtAnnotation || it is KtAnnotationEntry -> newList.add(it)
                elementType is KtModifierKeywordToken -> {
                    if (elementType == KtTokens.DEFAULT_VISIBILITY_KEYWORD) return@forEach
                    if (elementType == KtTokens.FINALLY_KEYWORD && !hasModifier(KtTokens.OVERRIDE_KEYWORD)) return@forEach
                    modifiers.add(it)
                }
            }
        }
        modifiers.sortBy { MODIFIERS_ORDER.indexOf(it.node.elementType) }
        modifiers.forEach { newList.add(it) }
    }
}