// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.builtins.isFunctionOrSuspendFunctionType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.extensions.DeclarationAttributeAltererExtension
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.references.mainReference
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
import org.jetbrains.kotlin.resolve.calls.util.getValueArgumentsInParentheses
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
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.psi.psiUtil.parents

fun KtLambdaArgument.moveInsideParentheses(bindingContext: BindingContext): KtCallExpression {
    val ktExpression = this.getArgumentExpression()
        ?: throw KotlinExceptionWithAttachments("no argument expression for $this")
            .withPsiAttachment("lambdaExpression", this)
    return moveInsideParenthesesAndReplaceWith(ktExpression, bindingContext)
}

fun KtLambdaArgument.moveInsideParenthesesAndReplaceWith(
    replacement: KtExpression,
    bindingContext: BindingContext
): KtCallExpression = moveInsideParenthesesAndReplaceWith(replacement, getLambdaArgumentName(bindingContext))


fun KtLambdaArgument.getLambdaArgumentName(bindingContext: BindingContext): Name? {
    val callExpression = parent as KtCallExpression
    val resolvedCall = callExpression.getResolvedCall(bindingContext)
    return (resolvedCall?.getArgumentMapping(this) as? ArgumentMatch)?.valueParameter?.name
}

fun KtLambdaArgument.moveInsideParenthesesAndReplaceWith(
    replacement: KtExpression,
    functionLiteralArgumentName: Name?,
    withNameCheck: Boolean = true,
): KtCallExpression {
    val oldCallExpression = parent as KtCallExpression
    val newCallExpression = oldCallExpression.copy() as KtCallExpression

    val psiFactory = KtPsiFactory(project)

    val argument =
        if (withNameCheck && shouldLambdaParameterBeNamed(newCallExpression.getValueArgumentsInParentheses(), oldCallExpression)) {
            psiFactory.createArgument(replacement, functionLiteralArgumentName)
        } else {
            psiFactory.createArgument(replacement)
        }

    val functionLiteralArgument = newCallExpression.lambdaArguments.firstOrNull()!!
    val valueArgumentList = newCallExpression.valueArgumentList ?: psiFactory.createCallArguments("()")

    valueArgumentList.addArgument(argument)

    (functionLiteralArgument.prevSibling as? PsiWhiteSpace)?.delete()
    if (newCallExpression.valueArgumentList != null) {
        functionLiteralArgument.delete()
    } else {
        functionLiteralArgument.replace(valueArgumentList)
    }
    return oldCallExpression.replace(newCallExpression) as KtCallExpression
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

private fun shouldLambdaParameterBeNamed(args: List<ValueArgument>, callExpr: KtCallExpression): Boolean {
    if (args.any { it.isNamed() }) return true
    val callee = (callExpr.calleeExpression?.mainReference?.resolve() as? KtFunction) ?: return false
    return if (callee.valueParameters.any { it.isVarArg }) true else callee.valueParameters.size - 1 > args.size
}

fun KtCallExpression.getLastLambdaExpression(): KtLambdaExpression? {
    if (lambdaArguments.isNotEmpty()) return null
    return valueArguments.lastOrNull()?.getArgumentExpression()?.unpackFunctionLiteral()
}

@OptIn(FrontendInternals::class)
fun KtCallExpression.canMoveLambdaOutsideParentheses(): Boolean {
    if (getStrictParentOfType<KtDelegatedSuperTypeEntry>() != null) return false
    val lastLambdaExpression = getLastLambdaExpression() ?: return false
    if (lastLambdaExpression.parentLabeledExpression()?.parentLabeledExpression() != null) return false

    val callee = calleeExpression
    if (callee is KtNameReferenceExpression) {
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

        if (candidates.isNotEmpty() && areAllCandidatesWithoutLastFunctionParameter) return false
    }

    return true
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

fun KtCallExpression.moveFunctionLiteralOutsideParentheses() {
    assert(lambdaArguments.isEmpty())
    val argumentList = valueArgumentList!!
    val argument = argumentList.arguments.last()
    val expression = argument.getArgumentExpression()!!
    assert(expression.unpackFunctionLiteral() != null)

    fun isWhiteSpaceOrComment(e: PsiElement) = e is PsiWhiteSpace || e is PsiComment
    val prevComma = argument.siblings(forward = false, withItself = false).firstOrNull { it.elementType == KtTokens.COMMA }
    val prevComments = (prevComma ?: argumentList.leftParenthesis)
        ?.siblings(forward = true, withItself = false)
        ?.takeWhile(::isWhiteSpaceOrComment)?.toList().orEmpty()
    val nextComments = argumentList.rightParenthesis
        ?.siblings(forward = false, withItself = false)
        ?.takeWhile(::isWhiteSpaceOrComment)?.toList()?.reversed().orEmpty()

    val psiFactory = KtPsiFactory(project)
    val dummyCall = psiFactory.createExpression("foo() {}") as KtCallExpression
    val functionLiteralArgument = dummyCall.lambdaArguments.single()
    functionLiteralArgument.getArgumentExpression()?.replace(expression)

    if (prevComments.any { it is PsiComment }) {
        if (prevComments.firstOrNull() !is PsiWhiteSpace) this.add(psiFactory.createWhiteSpace())
        prevComments.forEach { this.add(it) }
        prevComments.forEach { if (it is PsiComment) it.delete() }
    }
    this.add(functionLiteralArgument)
    if (nextComments.any { it is PsiComment }) {
        nextComments.forEach { this.add(it) }
        nextComments.forEach { if (it is PsiComment) it.delete() }
    }

    /* we should not remove empty parenthesis when callee is a call too - it won't parse */
    if (argumentList.arguments.size == 1 && calleeExpression !is KtCallExpression) {
        argumentList.delete()
    } else {
        argumentList.removeArgument(argument)
    }
}

fun KtBlockExpression.appendElement(element: KtElement, addNewLine: Boolean = false): KtElement {
    val rBrace = rBrace
    val newLine = KtPsiFactory(project).createNewLine()
    val anchor = if (rBrace == null) {
        val lastChild = lastChild
        lastChild as? PsiWhiteSpace ?: addAfter(newLine, lastChild)!!
    } else {
        rBrace.prevSibling!!
    }
    val addedElement = addAfter(element, anchor)!! as KtElement
    if (addNewLine) {
        addAfter(newLine, addedElement)
    }
    return addedElement
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

fun PsiElement.deleteSingle() {
    CodeEditUtil.removeChild(parent?.node ?: return, node ?: return)
}

fun KtClass.getOrCreateCompanionObject(): KtObjectDeclaration {
    companionObjects.firstOrNull()?.let { return it }
    return appendDeclaration(KtPsiFactory(project).createCompanionObject())
}

inline fun <reified T : KtDeclaration> KtClass.appendDeclaration(declaration: T): T {
    val body = getOrCreateBody()
    val anchor = PsiTreeUtil.skipSiblingsBackward(body.rBrace ?: body.lastChild!!, PsiWhiteSpace::class.java)
    val newDeclaration =
        if (anchor?.nextSibling is PsiErrorElement)
            body.addBefore(declaration, anchor)
        else
            body.addAfter(declaration, anchor)

    return newDeclaration as T
}

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

private fun KtDeclaration.predictImplicitModality(): KtModifierKeywordToken {
    if (this is KtClassOrObject) {
        if (this is KtClass && this.isInterface()) return KtTokens.ABSTRACT_KEYWORD
        return KtTokens.FINAL_KEYWORD
    }
    val klass = containingClassOrObject ?: return KtTokens.FINAL_KEYWORD
    if (hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
        if (klass.hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
            klass.hasModifier(KtTokens.OPEN_KEYWORD) ||
            klass.hasModifier(KtTokens.SEALED_KEYWORD)
        ) {
            return KtTokens.OPEN_KEYWORD
        }
    }
    if (klass is KtClass && klass.isInterface() && !hasModifier(KtTokens.PRIVATE_KEYWORD)) {
        return if (hasBody()) KtTokens.OPEN_KEYWORD else KtTokens.ABSTRACT_KEYWORD
    }
    return KtTokens.FINAL_KEYWORD
}

fun KtSecondaryConstructor.getOrCreateBody(): KtBlockExpression {
    bodyExpression?.let { return it }

    val delegationCall = getDelegationCall()
    val anchor = if (delegationCall.isImplicit) valueParameterList else delegationCall
    val newBody = KtPsiFactory(project).createEmptyBody()
    return addAfter(newBody, anchor) as KtBlockExpression
}

fun KtParameter.dropDefaultValue() {
    val from = equalsToken ?: return
    val to = defaultValue ?: from
    deleteChildRange(from, to)
}

fun KtTypeParameterListOwner.addTypeParameter(typeParameter: KtTypeParameter): KtTypeParameter? {
    typeParameterList?.let { return it.addParameter(typeParameter) }

    val list = KtPsiFactory(project).createTypeParameterList("<X>")
    list.parameters[0].replace(typeParameter)
    val leftAnchor = when (this) {
        is KtClass -> nameIdentifier
        is KtNamedFunction -> funKeyword
        is KtProperty -> valOrVarKeyword
        is KtTypeAlias -> nameIdentifier
        else -> null
    } ?: return null
    return (addAfter(list, leftAnchor) as KtTypeParameterList).parameters.first()
}

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

fun KtParameter.setDefaultValue(newDefaultValue: KtExpression): PsiElement {
    defaultValue?.let { return it.replaced(newDefaultValue) }

    val psiFactory = KtPsiFactory(project)
    val eq = equalsToken ?: add(psiFactory.createEQ())
    return addAfter(newDefaultValue, eq) as KtExpression
}

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