// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.getThisLabelName
import org.jetbrains.kotlin.idea.k2.refactoring.getThisReceiverOwner
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getOrCreateParameterList
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

private val counterpartNames = mapOf(
    "apply" to "also",
    "run" to "let",
    "also" to "apply",
    "let" to "run"
)

class ScopeFunctionConversionInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return callExpressionVisitor { expression ->
            val counterpartName = getCounterpart(expression)
            if (counterpartName != null) {
                holder.registerProblem(
                    expression.calleeExpression!!,
                    KotlinBundle.message("call.is.replaceable.with.another.scope.function"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    if (counterpartName == "also" || counterpartName == "let")
                        ConvertScopeFunctionToParameter(counterpartName)
                    else
                        ConvertScopeFunctionToReceiver(counterpartName)
                )
            }
        }
    }
}

/**
 * Determines if the given call expression can be converted to its counterpart scope function.
 * For example, 'apply' can be converted to 'also', 'run' to 'let', etc.
 *
 * @param expression The call expression to check
 * @return The name of the counterpart function, or null if conversion is not possible
 */
private fun getCounterpart(expression: KtCallExpression): String? {
    // Check if this is a call to a scope function with a lambda argument
    val callee = expression.calleeExpression as? KtNameReferenceExpression ?: return null
    val calleeName = callee.getReferencedName()
    val counterpartName = counterpartNames[calleeName] ?: return null
    val lambdaExpression = expression.lambdaArguments.singleOrNull()?.getLambdaExpression() ?: return null

    // Lambda must not have explicit parameters
    if (lambdaExpression.valueParameters.isNotEmpty()) {
        return null
    }

    var result: String? = null
    analyze(callee) {
        // Verify this is a call to a Kotlin standard library function with a receiver
        val resolvedCall = callee.resolveToCall()?.successfulCallOrNull<KaCall>() ?: return@analyze
        val symbol: KaCallableSymbol? = (resolvedCall as? KaCallableMemberCall<*, *>)?.partiallyAppliedSymbol?.symbol
        if (symbol?.dispatchReceiverType == null && symbol?.receiverType == null) return@analyze

        if (nameResolvesToStdlib(expression, calleeName, counterpartName)) {
            result = counterpartName
        }
    }
    return result
}

/**
 * Checks if the given name resolves to a standard library function.
 * This is done by creating a code fragment with the counterpart name and checking if it resolves to a function in the Kotlin package.
 *
 * @param expression The original call expression
 * @param calleeName The name of the original function
 * @param name The name of the counterpart function
 * @return True if the counterpart name resolves to a standard library function
 */
private fun nameResolvesToStdlib(expression: KtCallExpression, calleeName: String, name: String): Boolean {
    // Create a code fragment with the counterpart name
    val updatedExpressionText = expression.parent.text.replace(calleeName, name)
    val fragment = KtPsiFactory(expression.project).createExpressionCodeFragment(updatedExpressionText, expression.parent)

    return analyze(fragment) {
        // Resolve the symbol for the counterpart function
        val callableSymbol: KaCallableSymbol? = when (val fragmentExpression: KtExpression? = fragment.getContentElement()) {
            is KtDotQualifiedExpression -> {
                // Handle qualified expressions like "receiver.function()"
                val callExpression = fragmentExpression.selectorExpression as? KtCallExpression
                callExpression?.calleeExpression?.mainReference?.resolveToSymbol() as? KaCallableSymbol
            }
            else -> {
                // Handle other expressions
                val resolvedFragmentCall = fragmentExpression?.resolveToCall()?.successfulCallOrNull<KaCall>()
                (resolvedFragmentCall as? KaCallableMemberCall<*, *>)?.partiallyAppliedSymbol?.symbol
            }
        }

        // Check if the symbol is from the Kotlin standard library
        callableSymbol != null &&
        callableSymbol.callableId?.packageName?.asString() == "kotlin" &&
        callableSymbol.callableId?.callableName?.asString() == name
    }
}

class Replacement<T : PsiElement> private constructor(
    private val elementPointer: SmartPsiElementPointer<T>,
    private val replacementFactory: KtPsiFactory.(T) -> PsiElement
) {
    companion object {
        fun <T : PsiElement> create(element: T, replacementFactory: KtPsiFactory.(T) -> PsiElement): Replacement<T> {
            return Replacement(element.createSmartPointer(), replacementFactory)
        }
    }

    fun apply(factory: KtPsiFactory) {
        elementPointer.element?.let {
            it.replace(factory.replacementFactory(it))
        }
    }

    val endOffset: Int?
        get() = elementPointer.element?.endOffset
}

class ReplacementCollection(private val project: Project) {
    private val replacements = mutableListOf<Replacement<out PsiElement>>()
    var createParameter: KtPsiFactory.() -> PsiElement? = { null }
    var elementToRename: PsiElement? = null

    fun <T : PsiElement> add(element: T, replacementFactory: KtPsiFactory.(T) -> PsiElement) {
        replacements.add(Replacement.create(element, replacementFactory))
    }

    fun apply() {
        val factory = KtPsiFactory(project)
        elementToRename = factory.createParameter()

        // Calls need to be processed in outside-in order
        replacements.sortBy { it.endOffset }

        for (replacement in replacements) {
            replacement.apply(factory)
        }
    }

    fun isNotEmpty(): Boolean = replacements.isNotEmpty()
}

/**
 * Base class for quick fixes that convert between scope functions.
 * Handles the common logic for converting between 'let'/'run' and 'apply'/'also'.
 */
abstract class ConvertScopeFunctionFix(private val counterpartName: String) : KotlinModCommandQuickFix<KtNameReferenceExpression>() {
    override fun getFamilyName(): String = KotlinBundle.message("convert.scope.function.fix.family.name", counterpartName)

    override fun applyFix(
        project: Project,
        element: KtNameReferenceExpression,
        updater: ModPsiUpdater
    ) {
        val callExpression = element.parent as? KtCallExpression ?: return

        val lambda = callExpression.lambdaArguments.firstOrNull() ?: return
        val functionLiteral = lambda.getLambdaExpression()?.functionLiteral ?: return

        // Remove any existing parameter list and arrow
        functionLiteral.valueParameterList?.delete()
        functionLiteral.arrow?.delete()

        val replacements = ReplacementCollection(project)

        // Analyze the lambda and collect replacements
        analyze(lambda) {
            analyzeLambda(lambda, replacements)
        }

        // Replace the function name with its counterpart
        element.replace(KtPsiFactory(project).createExpression(counterpartName) as KtNameReferenceExpression)

        // Apply all collected replacements
        replacements.apply()

        // Perform any additional processing specific to the conversion type
        postprocessLambda(lambda)

        // Start in-place rename if needed
        if (replacements.isNotEmpty() && replacements.elementToRename != null && !isUnitTestMode()) {
            replacements.elementToRename?.startInPlaceRename()
        }
    }

    /**
     * Performs additional processing on the lambda after the conversion.
     * This involves shortening references.
     */
    protected abstract fun postprocessLambda(lambda: KtLambdaArgument)

    protected abstract fun KaSession.analyzeLambda(
        lambda: KtLambdaArgument,
        replacements: ReplacementCollection
    )
}

private fun PsiElement.startInPlaceRename() {
    val project = project
    val document = containingFile.viewProvider.document ?: return
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    if (editor.document == document) {
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

        editor.caretModel.moveToOffset(startOffset)
        KotlinVariableInplaceRenameHandler().doRename(this, editor, null)
    }
}

/**
 * Determines if a unique parameter name is needed for the lambda.
 * This is necessary when the default parameter name 'it' would conflict with existing declarations.
 *
 * @param lambdaArgument The lambda argument to check
 * @return True if a unique parameter name is needed
 */
fun KaSession.needUniqueNameForParameter(lambdaArgument: KtLambdaArgument): Boolean {
    // Check if 'it' is already used in the current scope
    val nameValidator = KotlinDeclarationNameValidator(
        visibleDeclarationsContext = lambdaArgument,
        checkVisibleDeclarationsContext = true,
        target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
    )

    // If 'it' is already invalid in the current scope, we need a unique name
    var needUniqueName = !nameValidator.validate(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier)

    // If 'it' is valid in the current scope, check nested scopes
    if (!needUniqueName) {
        lambdaArgument.accept(object : KtTreeVisitorVoid() {
            override fun visitDeclaration(dcl: KtDeclaration) {
                super.visitDeclaration(dcl)
                checkNeedUniqueName(dcl)
            }

            override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                super.visitLambdaExpression(lambdaExpression)
                // Check the first statement in the lambda body
                lambdaExpression.bodyExpression?.statements?.firstOrNull()?.let { checkNeedUniqueName(it) }
            }

            private fun checkNeedUniqueName(element: KtElement) {
                // Check if 'it' is valid in this nested scope
                val nestedValidator = KotlinDeclarationNameValidator(
                    visibleDeclarationsContext = element,
                    checkVisibleDeclarationsContext = true,
                    target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
                )

                if (!nestedValidator.validate(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier)) {
                    needUniqueName = true
                }
            }
        })
    }
    return needUniqueName
}

/**
 * Finds a unique parameter name for the lambda.
 * Tries to suggest a name based on the type of the receiver, or falls back to a generic name.
 *
 * @param lambdaArgument The lambda argument to find a name for
 * @return A unique parameter name
 */
private fun KaSession.findUniqueParameterName(lambdaArgument: KtLambdaArgument): String {
    // Create a name validator to check if suggested names are valid
    val nameValidator = KotlinDeclarationNameValidator(
        visibleDeclarationsContext = lambdaArgument,
        checkVisibleDeclarationsContext = true,
        target = KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
    )

    // Get the receiver type from the call expression
    val callExpression = lambdaArgument.getStrictParentOfType<KtCallExpression>()
    val resolvedCall = callExpression?.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
    val dispatchReceiver = resolvedCall?.partiallyAppliedSymbol?.dispatchReceiver
    val extensionReceiver = resolvedCall?.partiallyAppliedSymbol?.extensionReceiver
    val parameterType = dispatchReceiver?.type ?: extensionReceiver?.type

    // If we have a type, suggest a name based on it
    return if (parameterType != null) {
        with(KotlinNameSuggester()) {
            suggestTypeNames(parameterType).map { typeName ->
                KotlinNameSuggester.suggestNameByName(typeName) { nameValidator.validate(it) }
            }
        }.first()
    } else {
        // Otherwise, use a generic name like "p1", "p2", etc.
        KotlinNameSuggester.suggestNameByName("p") { candidate ->
            nameValidator.validate(candidate)
        }
    }
}

/**
 * Quick fix that converts a scope function with a receiver parameter to one with a regular parameter.
 * For example, converts 'run' to 'let' or 'apply' to 'also'.
 */
class ConvertScopeFunctionToParameter(counterpartName: String) : ConvertScopeFunctionFix(counterpartName) {

    override fun KaSession.analyzeLambda(
        lambda: KtLambdaArgument,
        replacements: ReplacementCollection
    ) {
        val project = lambda.project
        val factory = KtPsiFactory(project)
        val functionLiteral = lambda.getLambdaExpression()?.functionLiteral

        // Determine if we need a unique parameter name
        val parameterName = if (needUniqueNameForParameter(lambda)) {
            findUniqueParameterName(lambda)
        } else {
            StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
        }

        // Add a parameter to the lambda if needed
        if (functionLiteral != null && needUniqueNameForParameter(lambda)) {
            replacements.createParameter = {
                val lambdaParameterList = functionLiteral.getOrCreateParameterList()
                val parameterToAdd = createLambdaParameterList(parameterName).parameters.first()
                lambdaParameterList.addParameterBefore(parameterToAdd, lambdaParameterList.parameters.firstOrNull())
            }
        }

        // Process the lambda body to replace 'this' with the parameter name
        lambda.accept(object : KtTreeVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                super.visitSimpleNameExpression(expression)
                // Skip operation references like '+', '-', etc.
                if (expression is KtOperationReferenceExpression) return

                // Try to resolve the call
                val resolvedCall = expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() ?: return
                val dispatchReceiver: KaReceiverValue? = resolvedCall.partiallyAppliedSymbol.dispatchReceiver
                val extensionReceiver = resolvedCall.partiallyAppliedSymbol.extensionReceiver

                // Check if the receiver is from the function literal we're converting
                fun isReceiverFromFunctionLiteral(receiver: KaReceiverValue?): Boolean {
                    if (receiver is KaExplicitReceiverValue) return receiver.expression.mainReference?.resolve() == functionLiteral
                    if (receiver is KaImplicitReceiverValue) return receiver.getThisReceiverOwner()?.psi == functionLiteral
                    return false
                }

                // If the call is on the lambda's receiver, replace it with a call on the parameter
                if (isReceiverFromFunctionLiteral(dispatchReceiver) || isReceiverFromFunctionLiteral(extensionReceiver)) {
                    val parent = expression.parent
                    if (parent is KtCallExpression && expression == parent.calleeExpression) {
                        // Handle method calls: this.foo() -> paramName.foo()
                        if ((parent.parent as? KtQualifiedExpression)?.receiverExpression !is KtThisExpression) {
                            replacements.add(parent) { element ->
                                factory.createExpressionByPattern("$0.$1", parameterName, element)
                            }
                        }
                    } else if (parent is KtQualifiedExpression && parent.receiverExpression is KtThisExpression) {
                        // Skip already qualified expressions: this.foo -> paramName.foo (handled elsewhere)
                    } else {
                        // Handle property access: this.prop -> paramName.prop
                        val referencedName = expression.getReferencedName()
                        replacements.add(expression) {
                            createExpression("$parameterName.$referencedName")
                        }
                    }
                }
            }

            override fun visitThisExpression(expression: KtThisExpression) {
                // Replace 'this' with the parameter name
                if (expression.instanceReference.mainReference.resolve() == functionLiteral) {
                    replacements.add(expression) {
                        createExpression(parameterName)
                    }
                }
            }
        })
    }

    @OptIn(KaIdeApi::class)
    override fun postprocessLambda(lambda: KtLambdaArgument) {
        shortenReferences(
            lambda,
            shortenOptions = ShortenOptions(
                removeThisLabels = true,
                removeThis = false,
            )
        )
    }
}

/**
 * Quick fix that converts a scope function with a regular parameter to one with a receiver parameter.
 * For example, converts 'let' to 'run' or 'also' to 'apply'.
 */
class ConvertScopeFunctionToReceiver(counterpartName: String) : ConvertScopeFunctionFix(counterpartName) {
    override fun KaSession.analyzeLambda(
        lambda: KtLambdaArgument,
        replacements: ReplacementCollection
    ) {
        // Check if we need a unique parameter name (not used in this conversion, but needed for consistency)
        if (needUniqueNameForParameter(lambda)) {
            findUniqueParameterName(lambda)
        } else {
            StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
        }

        val functionLiteral = lambda.getLambdaExpression()?.functionLiteral

        // Process the lambda body to replace parameter references with 'this'
        lambda.accept(object : KtTreeVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                super.visitSimpleNameExpression(expression)

                // Check if this is a reference to the lambda parameter (e.g., 'it')
                if (expression.getReferencedNameAsName() == StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME
                    && expression.mainReference.resolve() == functionLiteral
                ) {
                    val parent = expression.parent
                    if (parent is KtDotQualifiedExpression) {
                        // Handle qualified expressions like 'it.foo'
                        if (expression == parent.receiverExpression) {
                            val selectorExpression = parent.selectorExpression
                            selectorExpression?.let {
                                // Replace 'it.foo' with just 'foo'
                                replacements.add(parent) {
                                    createExpression(selectorExpression.text)
                                }
                            }
                        }
                    } else {
                        // Replace 'it' with 'this'
                        replacements.add(expression) { createThisExpression() }
                    }
                } else {
                    // Handle implicit receiver references that need to be qualified
                    val resolvedCall = expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() ?: return
                    val dispatchReceiver = resolvedCall.partiallyAppliedSymbol.dispatchReceiver

                    // Only handle implicit receivers
                    if (dispatchReceiver !is KaImplicitReceiverValue) return

                    val symbol = dispatchReceiver.type.symbol
                    if (symbol !is KaDeclarationSymbol) return

                    val implicitReceiverValue = expression.resolveToCall()
                        ?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.dispatchReceiver as? KaImplicitReceiverValue

                    if (implicitReceiverValue == null) return

                    // Get the appropriate qualifier for this receiver
                    val thisQualifier = getThisQualifier(implicitReceiverValue)

                    val parent = expression.parent
                    if (parent is KtCallExpression && expression == parent.calleeExpression) {
                        // Handle method calls: foo() -> this@Qualifier.foo()
                        replacements.add(parent) { element ->
                            createExpressionByPattern("$thisQualifier.$0", element)
                        }
                    } else {
                        // Handle property access: prop -> this@Qualifier.prop
                        val referencedName = expression.getReferencedName()
                        replacements.add(expression) {
                            createExpression("$thisQualifier.$referencedName")
                        }
                    }
                }
            }

            /**
             * Determines the appropriate qualifier for 'this' expressions based on the receiver value.
             */
            context(session: KaSession)
            @OptIn(KaExperimentalApi::class)
            fun getThisQualifier(receiverValue: KaImplicitReceiverValue): String? {
                val symbol = receiverValue.symbol
                return when {
                    // For companion objects, use ContainingClass.CompanionName
                    (symbol as? KaClassSymbol)?.classKind == KaClassKind.COMPANION_OBJECT -> {
                        val containingClassName = (with (session) { symbol.containingSymbol } as KaClassifierSymbol).name?.asString() ?: ""
                        val companionName = symbol.name?.asString() ?: ""
                        "$containingClassName.$companionName"
                    }
                    // For objects, use the object name
                    (symbol as? KaClassSymbol)?.classKind == KaClassKind.OBJECT -> {
                        symbol.name?.asString()
                    }
                    // For classes, use this@ClassName
                    symbol is KaClassifierSymbol && symbol !is KaAnonymousObjectSymbol -> {
                        val className = (symbol.psi as? PsiClass)?.name ?: symbol.name?.asString()
                        if (className != null) "this@$className" else "this"
                    }
                    // For functions, use this@FunctionName if available
                    symbol is KaReceiverParameterSymbol && 
                    (symbol.owningCallableSymbol is KaNamedSymbol || symbol.owningCallableSymbol is KaAnonymousFunctionSymbol) -> {
                        symbol.owningCallableSymbol.getThisLabelName()?.let { "this@$it" } ?: "this"
                    }
                    // Default case
                    else -> "this"
                }
            }

            override fun visitThisExpression(expression: KtThisExpression) {
                // Handle 'this' expressions that need to be qualified
                val implicitReceiverValue = expression.resolveToCall()
                    ?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.dispatchReceiver as? KaImplicitReceiverValue

                if (implicitReceiverValue == null) {
                    // If we can't get an implicit receiver, try to get the class name directly
                    val className = (expression.instanceReference.mainReference.resolve() as? KtClass)?.name ?: return
                    // Replace with a qualified 'this' expression
                    replacements.add(expression) { createThisExpression(className) }
                } else {
                    // Get the appropriate qualifier for this receiver
                    val thisQualifier = getThisQualifier(implicitReceiverValue)
                    // Replace with a qualified 'this' expression
                    thisQualifier?.let { replacements.add(expression) { createExpression(thisQualifier) } }
                }
            }
        })
    }

    /**
     * Performs additional processing on the lambda after the conversion.
     * For conversion to receiver, we want to remove redundant 'this' references but keep 'this@' labels.
     */
    @OptIn(KaIdeApi::class)
    override fun postprocessLambda(lambda: KtLambdaArgument) {
        shortenReferences(
            lambda,
            shortenOptions = ShortenOptions(
                removeThisLabels = true,  // Keep 'this@' labels
                removeThis = true          // Remove redundant 'this' references
            )
        )
    }
}
