// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.applyFromWithConversion
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.applyToWithConversion
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.counterpartNames
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.findUniqueParameterName
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.getThisLabelName
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.hasImplicitItConflicts
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.isReceiverFromFunctionLiteral
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.isSimpleLambdaParameterCase
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.nameResolvesToStdlib
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.usesExplicitParameter
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.usesImplicitThis
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getOrCreateParameterList
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class ScopeFunctionConversionInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
         return callExpressionVisitor { expression ->
            val counterpartNames = getCounterparts(expression)
            if (counterpartNames.isNotEmpty()) {
                val calleeName = expression.calleeExpression?.text ?: ""
                val quickFixes = counterpartNames.flatMap { counterpartName ->
                    when {
                        // Converting from implicit 'this' to explicit parameter
                        usesImplicitThis(calleeName) && usesExplicitParameter(counterpartName) -> 
                            listOf(ConvertScopeFunctionToParameter(counterpartName))
                        
                        // Converting from explicit parameter to implicit 'this'
                        usesExplicitParameter(calleeName) && usesImplicitThis(counterpartName) -> 
                            listOf(ConvertScopeFunctionToReceiver(counterpartName))
                        
                        // Both use implicit 'this' or both use explicit parameter - offer both options
                        else -> listOf(
                            ConvertScopeFunctionToReceiver(counterpartName),
                            ConvertScopeFunctionToParameter(counterpartName)
                        )
                    }
                }.toTypedArray()

                expression.calleeExpression?.let {
                    holder.registerProblem(
                        it,
                        KotlinBundle.message("call.is.replaceable.with.another.scope.function"),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        *quickFixes
                    )
                }
            }
        }
    }
}

/**
 * Determines which counterpart scope functions the given call expression can be converted to.
 * For example, 'apply' can be converted to 'also', 'run' to 'let', 'with' to both 'run' and 'let'.
 *
 * @param expression The call expression to check
 * @return List of counterpart function names that are valid conversions
 */
private fun getCounterparts(expression: KtCallExpression): List<String> {
    val callee = expression.calleeExpression as? KtNameReferenceExpression ?: return emptyList()
    val calleeName = callee.getReferencedName()
    val counterpartCandidates = when (val counterpart = counterpartNames[calleeName]) {
        is String -> listOf(counterpart)
        is List<*> -> counterpart.filterIsInstance<String>()
        else -> return emptyList()
    }.takeIf { it.isNotEmpty() } ?: return emptyList()

    val lambdaExpression = expression.lambdaArguments.singleOrNull()?.getLambdaExpression() ?: return emptyList()
    
    if (lambdaExpression.valueParameters.isNotEmpty() && !isSimpleLambdaParameterCase(lambdaExpression)) {
        return emptyList()
    }

    return analyze(callee) {
        val resolvedCall = callee.resolveToCall()?.successfulCallOrNull<KaCall>() ?: return@analyze emptyList()
        val symbol = (resolvedCall as? KaCallableMemberCall<*, *>)?.partiallyAppliedSymbol?.symbol ?: return@analyze emptyList()
        if (symbol.receiverType == null && (symbol as? KaNamedFunctionSymbol)?.name?.asString() != "with") return@analyze emptyList()

        counterpartCandidates.filter { counterpartName ->
            if (!nameResolvesToStdlib(expression, calleeName, counterpartName)) return@filter false
            
            // Don't suggest "with" conversion if the parameter is nullable
            if (counterpartName == "with") {
                val callReceiver = (expression.parent as? KtQualifiedExpression)?.receiverExpression
                if (callReceiver != null) {
                    val actualReceiverType = callReceiver.expressionType
                    if (actualReceiverType?.isNullable == true) return@filter false
                }
            }
            true
        }
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
abstract class ConvertScopeFunctionFix(protected val counterpartName: String) : KotlinModCommandQuickFix<KtNameReferenceExpression>() {
    override fun getFamilyName(): String = KotlinBundle.message("convert.scope.function.fix.family.name", counterpartName)

    override fun applyFix(
        project: Project,
        element: KtNameReferenceExpression,
        updater: ModPsiUpdater
    ) {
        val callExpression = element.parent as? KtCallExpression ?: return
        val originalCalleeName = element.getReferencedName()
        val lambda = callExpression.lambdaArguments.firstOrNull() ?: return
        val functionLiteral = lambda.getLambdaExpression()?.functionLiteral ?: return
        val factory = KtPsiFactory(project)

        val replacements = ReplacementCollection(project)

        // Analyze the lambda and collect replacements BEFORE deleting a parameter list
        analyze(lambda) {
            analyzeLambda(lambda, replacements)
        }

        // Remove any existing parameter list and arrow AFTER processing
        functionLiteral.valueParameterList?.delete()
        functionLiteral.arrow?.delete()

        // Handle different structural transformations based on function characteristics
        val newLambda = when {
            originalCalleeName == "with" -> {
                // Converting FROM 'with': with(receiver) -> receiver.counterpartName
                if (applyFromWithConversion(callExpression, element, counterpartName, factory)) {
                    replacements.apply()
                    lambda // Lambda is still valid for postprocessing
                } else null
            }
            counterpartName == "with" -> {
                // Converting TO 'with': receiver.originalCalleeName -> with(receiver)
                // Apply replacements to the lambda BEFORE doing structural transformation
                replacements.apply()
                val replacedElement = applyToWithConversion(element, counterpartName, lambda, factory)
                // Get the new lambda from the replaced element
                replacedElement?.lambdaArguments?.firstOrNull()
            }
            else -> {
                // For regular scope functions, just replace the function name with its counterpart
                element.replace(factory.createExpression(counterpartName) as KtNameReferenceExpression)
                replacements.apply()
                lambda // Lambda is still valid for postprocessing
            }
        }

        // Perform any additional processing specific to the conversion type
        newLambda?.let { postprocessLambda(it) }

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
 * Visitor that replaces implicit 'this' receiver calls with explicit parameter calls.
 * Used when converting from receiver-based scope functions (like 'run') to parameter-based ones (like 'let').
 */
private class ReceiverToParameterVisitor(
    private val functionLiteral: KtFunctionLiteral?,
    private val replacements: ReplacementCollection,
    private val parameterName: String,
    private val factory: KtPsiFactory,
    private val session: KaSession
) : KtTreeVisitorVoid() {

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        super.visitSimpleNameExpression(expression)
        // Skip operation references like '+', '-', etc.
        if (expression is KtOperationReferenceExpression) return

        // Try to resolve the call
        val resolvedCall = with(session) { 
            expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() 
        } ?: return
        val dispatchReceiver: KaReceiverValue? = resolvedCall.partiallyAppliedSymbol.dispatchReceiver
        val extensionReceiver = resolvedCall.partiallyAppliedSymbol.extensionReceiver

        // If the call is on the lambda's receiver, replace it with a call on the parameter
        if (with(session) { isReceiverFromFunctionLiteral(dispatchReceiver, functionLiteral) } || 
            with(session) { isReceiverFromFunctionLiteral(extensionReceiver, functionLiteral) }) {
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

        // Determine the parameter name to use
        val parameterName = when {
            // Lambda already has explicit parameters - don't create new ones
            functionLiteral?.valueParameters?.isNotEmpty() == true -> {
                functionLiteral.valueParameters.first().name ?: StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
            }
            // Lambda needs a unique parameter name
            usesExplicitParameter(counterpartName) && hasImplicitItConflicts(lambda) -> {
                val uniqueName = findUniqueParameterName(lambda)
                // Create the parameter for an implicit case
                replacements.createParameter = {
                    val lambdaParameterList = functionLiteral?.getOrCreateParameterList()
                    val parameterToAdd = createLambdaParameterList(uniqueName).parameters.first()
                    lambdaParameterList?.addParameterBefore(parameterToAdd, lambdaParameterList.parameters.firstOrNull())
                }
                uniqueName
            }
            // Use default 'it'
            else -> StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
        }

        // Process the lambda body to replace 'this' with the parameter name
        val visitor = ReceiverToParameterVisitor(functionLiteral, replacements, parameterName, factory, this)
        lambda.accept(visitor)
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
 * Visitor that replaces explicit parameter calls with implicit 'this' receiver calls.
 * Used when converting from parameter-based scope functions (like 'let') to receiver-based ones (like 'run').
 */
private class ParameterToReceiverVisitor(
    private val functionLiteral: KtFunctionLiteral?,
    private val replacements: ReplacementCollection,
    private val session: KaSession
) : KtTreeVisitorVoid() {
    
    /**
     * Gets the parameter name from the function literal (must be called before parameter list deletion).
     */
    private fun getParameterName(): String {
        return functionLiteral?.valueParameters?.firstOrNull()?.name ?: StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
    }

    /**
     * Checks if the given parameter reference belongs to our target lambda.
     * This prevents converting parameters from nested lambdas.
     */
    private fun belongsToTargetLambda(expression: KtSimpleNameExpression): Boolean {
        val resolved = expression.mainReference.resolve()
        
        // For explicit parameters, check if the parameter belongs to our function literal
        if (resolved is KtParameter) {
            return resolved.ownerFunction == functionLiteral
        }
        
        // For implicit 'it' parameters, find the nearest enclosing lambda
        var current: PsiElement? = expression
        while (current != null) {
            if (current is KtFunctionLiteral) {
                return current == functionLiteral
            }
            current = current.parent
        }
        
        return false
    }

    /**
     * Helper method to handle qualified expressions like 'param.foo' -> 'foo'
     */
    private fun handleQualifiedExpression(parent: KtDotQualifiedExpression) {
        val selectorExpression = parent.selectorExpression
        selectorExpression?.let {
            // Check if we're inside a string template entry
            val stringTemplateEntry = parent.parent as? KtStringTemplateEntryWithExpression
            if (stringTemplateEntry != null && selectorExpression is KtSimpleNameExpression) {
                // Replace ${param.foo} with $foo (no braces for simple identifier)
                replacements.add(stringTemplateEntry) { _ ->
                    // Create a temporary string template to extract the simple reference entry
                    val tempString = createExpression("\"\$${selectorExpression.text}\"") as KtStringTemplateExpression
                    tempString.entries.first()
                }
            } else {
                // Replace 'param.foo' with just 'foo'
                replacements.add(parent) {
                    createExpression(selectorExpression.text)
                }
            }
        }
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        super.visitSimpleNameExpression(expression)

        // Check if this expression should be converted to 'this'
        val parameterName = getParameterName()
        val shouldConvert = (expression.getReferencedName() == parameterName && belongsToTargetLambda(expression)) ||
                           (expression.getReferencedNameAsName() == StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME && 
                            expression.mainReference.resolve() == functionLiteral)

        if (shouldConvert) {
            val parent = expression.parent
            if (parent is KtDotQualifiedExpression && expression == parent.receiverExpression) {
                // Handle qualified expressions like 't.trim()' or 'it.foo'
                handleQualifiedExpression(parent)
            } else {
                // Replace parameter with 'this'
                replacements.add(expression) { createThisExpression() }
            }
            return
        } else {
            // Handle implicit receiver references that need to be qualified
            val resolvedCall = with(session) { 
                expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() 
            } ?: return
            val dispatchReceiver = resolvedCall.partiallyAppliedSymbol.dispatchReceiver

            // Skip if this is the receiver from the lambda we're converting (e.g., 'with' to 'run')
            if (with(session) { isReceiverFromFunctionLiteral(dispatchReceiver, functionLiteral) }) return

            // Only handle implicit receivers
            if (dispatchReceiver !is KaImplicitReceiverValue) return

            val symbol = dispatchReceiver.type.symbol
            if (symbol !is KaDeclarationSymbol) return

            val implicitReceiverValue = with(session) { 
                expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.dispatchReceiver as? KaImplicitReceiverValue 
            } ?: return

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
    @OptIn(KaExperimentalApi::class)
    private fun getThisQualifier(receiverValue: KaImplicitReceiverValue): String? {
        val symbol = receiverValue.symbol
        return when {
            // For companion objects, use ContainingClass.CompanionName
            (symbol as? KaClassSymbol)?.classKind == KaClassKind.COMPANION_OBJECT -> {
                val containingClassName = (with(session) { symbol.containingSymbol } as KaClassifierSymbol).name?.asString() ?: ""
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
        val implicitReceiverValue = with(session) { 
            expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.dispatchReceiver as? KaImplicitReceiverValue
        }

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
        if (usesExplicitParameter(counterpartName) && hasImplicitItConflicts(lambda)) {
            findUniqueParameterName(lambda)
        } else {
            StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
        }

        val functionLiteral = lambda.getLambdaExpression()?.functionLiteral

        // Process the lambda body to replace parameter references with 'this'
        val visitor = ParameterToReceiverVisitor(functionLiteral, replacements, this)
        lambda.accept(visitor)
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
