// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.collections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.number
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.EditCommaSeparatedListHelper
import org.jetbrains.kotlin.idea.base.psi.appendTypeArgument
import org.jetbrains.kotlin.idea.base.psi.appendValueArgument
import org.jetbrains.kotlin.idea.base.psi.getOrCreateValueArgumentList
import org.jetbrains.kotlin.idea.base.psi.relativeTo
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.ThisRebinderForAddingNewReceiver
import org.jetbrains.kotlin.idea.codeinsight.utils.getTopmostParenthesizedExpressionOrSelf
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.expressionVisitor

/**
 * Applicable to a topmost [KtBinaryExpression] possibly wrapped into `()`
 */
class CollectionConcatenationToBuildCollectionInspection :
    KotlinApplicableInspectionBase.Simple<KtExpression, CollectionConcatenationToBuildCollectionInspection.Context>() {

    var numberOfOperationsThreshold: Int = 2

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = expressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getOptionsPane(): OptPane =
        pane(number("numberOfOperationsThreshold", KotlinBundle.message("number.of.operations.threshold"), 2, 100))

    override fun getProblemDescription(element: KtExpression, context: Context): String =
        KotlinBundle.message("collection.concatenation.can.be.converted.to.build.collection")

    override fun getProblemHighlightType(element: KtExpression, context: Context): ProblemHighlightType =
        if (context.operations.size > numberOfOperationsThreshold) {
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        } else {
            ProblemHighlightType.INFORMATION
        }

    override fun isApplicableByPsi(element: KtExpression): Boolean {
        if (element is KtParameter) return false
        // sub expression elements are/will be visited
        if (element is KtBinaryExpression) return false
        // do not report on arguments
        val parent = element.parent
        if (element is KtNameReferenceExpression && parent is KtValueArgument) return false
        if (parent is KtParenthesizedExpression) {
            // we only care about the topmost `KtParenthesizedExpression` expression
            return false
        }
        val expression = element.safeDeparenthesize()
        val expressionToConvert = expression.expressionToConvert()
        return when (expressionToConvert) {
            is KtBinaryExpression -> {
                if (expression is KtBinaryExpression && expressionToConvert != expression) {
                    // we only care about the topmost `KtBinaryExpression` expression
                    return false
                }
                expressionToConvert.hasApplicableToken() && isApplicablePossiblyNestedBinaryExpression(expressionToConvert)
            }

            else -> true
        }
    }

    override fun getApplicableRanges(element: KtExpression): List<TextRange> {
        val expression = element.safeDeparenthesize()
        val expressionToConvert = expression.expressionToConvert()
        return when {
            expressionToConvert is KtBinaryExpression && expressionToConvert == expression ->
                // applicable only on the `+`/`-` operators
                nestedBinaryExpressionSequence(expressionToConvert)
                    .mapTo(mutableListOf()) { binaryExpression ->
                        binaryExpression.operationReference.textRange.relativeTo(element)
                    }

            else -> listOf(TextRange(0, element.textLength))
        }
    }

    private fun isApplicablePossiblyNestedBinaryExpression(element: KtBinaryExpression): Boolean {
        return nestedBinaryExpressionSequence(element).all { binaryExpression ->
            binaryExpression.hasApplicableToken()
                    && binaryExpression.right != null // check if no syntax errors
        }
    }

    /**
     * Generates a sequence of binary expressions, where the result of `a + b + c` is represented as
     * `sequenceOf((a + b) + c, a + b)`.
     *
     * Parentheses are intentionally not ignored, so expressions like `(b + c)` are considered non-binary
     * expressions in cases such as `a + (b + c) + d`. Thus, they will not be converted to the `build<COLLECTION>` call.
     */
    private fun nestedBinaryExpressionSequence(element: KtBinaryExpression): Sequence<KtBinaryExpression> {
        return generateSequence(element) { it.left as? KtBinaryExpression }
    }

    private fun KtBinaryExpression.hasApplicableToken(): Boolean {
        return operationToken == KtTokens.PLUS || operationToken == KtTokens.MINUS
    }

    override fun KaSession.prepareContext(element: KtExpression): Context? {
        val expressionToConvert = element.safeDeparenthesize().expressionToConvert()
        val expressionType = expressionToConvert.expressionType ?: return null
        val collectionType = when {
            expressionType.isClassType(StandardClassIds.List) -> Context.CollectionType.List
            expressionType.isClassType(StandardClassIds.Set) -> Context.CollectionType.Set
            else -> return null
        }
        if (expressionToConvert !is KtBinaryExpression && expressionToConvert.isBuildCollectionCall(collectionType)) return null

        val operations = when (expressionToConvert) {
            is KtBinaryExpression -> buildList {
                val initialOperation = createInitialOperation(expressionToConvert) ?: return null
                add(initialOperation)

                for (binaryExpression in nestedBinaryExpressionSequence(expressionToConvert).toList().reversed()) {
                    val operation = binaryExpression.toOperation() ?: return null
                    add(operation)
                }
            }

            else -> listOf(expressionToConvert.toOperationForStandalone() ?: return null)
        }

        val rebinderContext = ThisRebinderForAddingNewReceiver.createContext(expressionToConvert)
        return Context(collectionType, operations, rebinderContext)
    }

    context(_: KaSession)
    private fun createInitialOperation(element: KtBinaryExpression): Context.Operation? {
        val expression = nestedBinaryExpressionSequence(element).lastOrNull()?.left ?: return null
        val type = expression.expressionType ?: return null
        return expression.toOperationForPlus(isIterableOrSequence(type))
    }

    context(_: KaSession)
    private fun KtExpression.toOperationForStandalone(): Context.Operation? {
        val type = expressionType ?: return null
        return toOperationForPlus(isIterableOrSequence(type))
    }

    context(_: KaSession)
    private fun KtExpression.isBuildCollectionCall(collectionType: Context.CollectionType): Boolean {
        val callExpression = transformingCallExpression() ?: return false
        val resolvedTo = callExpression.calleeExpression?.mainReference?.resolveToSymbol() as? KaCallableSymbol ?: return false
        return resolvedTo.callableId?.asSingleFqName()?.asString() == collectionType.buildCallFqName
    }

    context(_: KaSession)
    private fun KtBinaryExpression.toOperation(): Context.Operation? {
        if (!operationReference.isDefaultStdlibCollectionOperation()) return null
        val rhs = right ?: return null
        val rhsType = rhs.expressionType ?: return null

        val isCollectionOrSequence = isIterableOrSequence(rhsType)
        return when (operationToken) {
            KtTokens.MINUS -> when {
                isCollectionOrSequence -> Context.Operation.AddRemoveOperation(
                    rhs.createSmartPointer(),
                    Context.Operation.AddRemoveOperation.Kind.RemoveAll
                )

                else -> Context.Operation.AddRemoveOperation(
                    rhs.createSmartPointer(),
                    Context.Operation.AddRemoveOperation.Kind.Remove
                )
            }

            KtTokens.PLUS -> rhs.toOperationForPlus(isCollectionOrSequence)

            else -> null
        }
    }

    context(_: KaSession)
    private fun isIterableOrSequence(rhsType: KaType): Boolean {
        return rhsType.isSubtypeOf(StandardClassIds.Iterable) || rhsType.isSubtypeOf(StandardClassIds.Sequence)
    }

    context(_: KaSession)
    private fun KtExpression.toOperationForPlus(isCollectionOrSequence: Boolean): Context.Operation {
        toTransformingOperation()?.let { return it }
        return when {
            isCollectionOrSequence ->
                Context.Operation.AddRemoveOperation(createSmartPointer(), Context.Operation.AddRemoveOperation.Kind.AddAll)

            else ->
                Context.Operation.AddRemoveOperation(createSmartPointer(), Context.Operation.AddRemoveOperation.Kind.Add)
        }
    }

    context(_: KaSession)
    private fun KtExpression.toTransformingOperation(): Context.Operation.TransformingOperation? {
        if (this !is KtQualifiedExpression) return null
        val callExpression = selectorExpression as? KtCallExpression ?: return null
        val resolvedTo = callExpression.calleeExpression?.mainReference?.resolveToSymbol() as? KaCallableSymbol ?: return null
        val fqName = resolvedTo.callableId?.asSingleFqName()?.asString() ?: return null
        val kind = Context.Operation.TransformingOperation.Kind.byCallFqName(fqName) ?: return null
        return Context.Operation.TransformingOperation(createSmartPointer(), kind)
    }


    /**
     * The `+`/`-` operators may be overridden by the user, so we ensure that these operations are from the standard library.
     */
    context(_: KaSession)
    private fun KtOperationReferenceExpression.isDefaultStdlibCollectionOperation(): Boolean {
        val resolvedTo = mainReference.resolveToSymbol() as? KaCallableSymbol ?: return false
        return resolvedTo.callableId?.packageName == StandardNames.COLLECTIONS_PACKAGE_FQ_NAME
    }

    override fun createQuickFix(
        element: KtExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtExpression> = object : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("collection.concatenation.to.build.collection.call.fix.text")

        override fun applyFix(
            project: Project,
            element: KtExpression,
            updater: ModPsiUpdater,
        ) {
            this@CollectionConcatenationToBuildCollectionInspection.applyFix(project, element, context, updater)
        }
    }

    private fun applyFix(
        project: Project,
        element: KtExpression,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        if (!elementContext.isValid()) return

        val writableElement = updater.getWritable(element)
        val writableOperations = elementContext.operations.map { operation ->
            operation.toWritable(updater) ?: return
        }
        val writableRebinderContext = elementContext.rebinderContext.toWritable(updater) ?: return

        val expression = writableElement.safeDeparenthesize()
        val expressionToConvert = expression.expressionToConvert()
        val replacementTarget = expressionToConvert.getTopmostParenthesizedExpressionOrSelf()
        val replacements = ThisRebinderForAddingNewReceiver.apply(writableRebinderContext)

        val ktPsiFactory = KtPsiFactory(project)
        val buildCall = createBuildCallExpression(ktPsiFactory, expressionToConvert, elementContext)
        val bodyExpression = getSingleLambdaArgumentBody(buildCall)

        /**
         * [ThisRebinderForAddingNewReceiver] may replace some of our operands, so we watch on replacements to use correct PsiElement
         */
        fun KtExpression.elementOrReplacement() =
            replacements[this] ?: this

        val collectionType = elementContext.collectionType
        for (operation in writableOperations) {
            when (operation) {
                is Context.Operation.AddRemoveOperation -> {
                    val expression = operation.expression.element?.elementOrReplacement()
                        ?: error("Element is invalid for operation: $operation")
                    bodyExpression.addOperation(ktPsiFactory, collectionType.operationNames.getValue(operation.kind), expression)
                }

                is Context.Operation.TransformingOperation -> {
                    val expression = operation.expression.element?.elementOrReplacement()
                        ?: error("Element is invalid for operation: $operation")
                    val callExpression = expression.transformingCallExpression() ?: return
                    val nameReferenceExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return
                    val identifier = nameReferenceExpression.getIdentifier() ?: return
                    val valueArgumentList = callExpression.getOrCreateValueArgumentList()
                    valueArgumentList.appendValueArgument(ktPsiFactory.createArgument(ktPsiFactory.createThisExpression()))
                    identifier.replace(ktPsiFactory.createIdentifier(operation.kind.toCallShortName))

                    val typeArgumentsList = callExpression.typeArgumentList
                    if (typeArgumentsList != null && typeArgumentsList.arguments.isNotEmpty()) {
                        // If we have explicit type arguments,
                        // we add a new one because the `To` collection operators have an additional type argument
                        // for the result collection type, which can be inferred.
                        val newTypeArgument = ktPsiFactory.createTypeArgument("_")
                        when (operation.kind.resultCollectionTypeArgumentPosition) {
                            Context.Operation.TransformingOperation.Kind.ResultCollectionTypeArgumentPosition.First -> {
                                EditCommaSeparatedListHelper.addItemBefore(
                                    list = typeArgumentsList,
                                    allItems = typeArgumentsList.arguments,
                                    item = newTypeArgument,
                                    anchor = typeArgumentsList.arguments.first()
                                )
                            }
                            Context.Operation.TransformingOperation.Kind.ResultCollectionTypeArgumentPosition.Last -> {
                                callExpression.appendTypeArgument(newTypeArgument)
                            }
                        }
                    }
                    bodyExpression.addStatement(ktPsiFactory, expression)
                }
            }
        }
        replacementTarget.replace(buildCall) as KtExpression
    }

    private fun Context.Operation.toWritable(updater: ModPsiUpdater): Context.Operation? {
        val writableExpression = updater.getWritable(expression.element ?: return null).createSmartPointer()
        return when (this) {
            is Context.Operation.AddRemoveOperation -> Context.Operation.AddRemoveOperation(writableExpression, kind)
            is Context.Operation.TransformingOperation -> Context.Operation.TransformingOperation(writableExpression, kind)
        }
    }

    private fun ThisRebinderForAddingNewReceiver.Context.toWritable(
        updater: ModPsiUpdater,
    ): ThisRebinderForAddingNewReceiver.Context? {
        val callsToAddImplicitReceiver = callsToAddImplicitReceiver.map { call ->
            val element = call.call.element ?: return null
            ThisRebinderForAddingNewReceiver.Context.CallToAddImplicitReceiver(
                updater.getWritable(element).createSmartPointer(),
                call.labelToAdd,
            )
        }
        val thisExpressionsToAddLabels = thisExpressionsToAddLabels.map { thisExpression ->
            val element = thisExpression.thisExpression.element ?: return null
            ThisRebinderForAddingNewReceiver.Context.ThisExpressionToAddLabel(
                updater.getWritable(element).createSmartPointer(),
                thisExpression.labelToAdd,
            )
        }
        return ThisRebinderForAddingNewReceiver.Context(callsToAddImplicitReceiver, thisExpressionsToAddLabels, project)
    }

    private fun getSingleLambdaArgumentBody(buildCall: KtCallExpression): KtBlockExpression {
        val singleLambdaArgument = buildCall.lambdaArguments.single()
        val lambdaExpression = singleLambdaArgument.getLambdaExpression()
            ?: error("Expected lambda argument for lambda expression created by KtPsiFactory")
        return lambdaExpression.bodyExpression
            ?: error("Expected body expression for lambda expression created by KtPsiFactory")
    }

    private fun createBuildCallExpression(
        ktPsiFactory: KtPsiFactory,
        element: KtExpression,
        elementContext: Context
    ): KtCallExpression {
        val shortName = elementContext.collectionType.buildCallShortName
        val label = when (shortName) {
            in elementContext.rebinderContext.allLabels(),
            in element.getConflictingLabels() -> element.generateNonConflictingLabel(shortName) + "@"

            else -> ""
        }
        val dotQualifiedExpression =
            ktPsiFactory.createExpression("${elementContext.collectionType.buildCallFqName} $label{ }") as KtDotQualifiedExpression
        return dotQualifiedExpression.selectorExpression as KtCallExpression
    }

    private fun KtExpression.generateNonConflictingLabel(shortName: String): String {
        val conflictingLabels = getConflictingLabels()
        return generateSequence(1) { it + 1 }
            .firstNotNullOf { i ->
                "$shortName$i".takeUnless { it in conflictingLabels }
            }
    }

    private fun KtExpression.getConflictingLabels(): Set<String> {
        return parents(withSelf = true)
            .takeWhile { it !is KtFile }
            .mapNotNullTo(mutableSetOf()) { parent ->
                when (parent) {
                    is KtLabeledExpression -> parent.getLabelName()
                    is KtCallExpression -> (parent.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
                    else -> null
                }
            }
    }

    private fun KtExpression.expressionToConvert(): KtExpression {
        var currentExpression: KtExpression = this
        var topmostBinaryExpression: KtBinaryExpression? = currentExpression as? KtBinaryExpression
        while (true) {
            val parent = currentExpression.parent
            currentExpression = when (parent) {
                is KtParenthesizedExpression -> parent
                is KtQualifiedExpression -> parent
                is KtBinaryExpression -> {
                    topmostBinaryExpression = parent
                    parent
                }

                else -> break
            }
        }
        return topmostBinaryExpression ?: currentExpression
    }

    private fun KtExpression.transformingCallExpression(): KtCallExpression? {
        return when (this) {
            is KtCallExpression -> this
            is KtQualifiedExpression -> selectorExpression as? KtCallExpression
            else -> null
        }
    }

    private fun KtBlockExpression.addOperation(ktPsiFactory: KtPsiFactory, functionName: String, expression: KtExpression) {
        val call = ktPsiFactory.createExpression("$functionName()") as KtCallExpression
        val valueArgumentList = call.valueArgumentList
            ?: error("Expected value argument list for call expression created by KtPsiFactory")
        valueArgumentList.appendValueArgument(ktPsiFactory.createArgument(expression.safeDeparenthesize()))
        addStatement(ktPsiFactory, call)
    }


    private fun KtBlockExpression.addStatement(ktPsiFactory: KtPsiFactory, statement: KtExpression) {
        addBefore(statement, rBrace)
        addBefore(ktPsiFactory.createNewLine(), rBrace)
    }

    class Context(
        val collectionType: CollectionType,
        val operations: List<Operation>,
        val rebinderContext: ThisRebinderForAddingNewReceiver.Context,
    ) {
        fun isValid(): Boolean {
            if (operations.any { it.expression.element == null }) return false
            if (!rebinderContext.isValid()) return false
            return true
        }

        enum class CollectionType(
            val buildCallFqName: String,
            val operationNames: Map<Operation.AddRemoveOperation.Kind, String>,
        ) {
            List(
                buildCallFqName = "kotlin.collections.buildList",
                operationNames = mapOf(
                    Operation.AddRemoveOperation.Kind.Add to "add",
                    Operation.AddRemoveOperation.Kind.Remove to "remove",
                    Operation.AddRemoveOperation.Kind.AddAll to "addAll",
                    Operation.AddRemoveOperation.Kind.RemoveAll to "removeAll"
                )
            ),
            Set(
                buildCallFqName = "kotlin.collections.buildSet",
                operationNames = mapOf(
                    Operation.AddRemoveOperation.Kind.Add to "add",
                    Operation.AddRemoveOperation.Kind.Remove to "remove",
                    Operation.AddRemoveOperation.Kind.AddAll to "addAll",
                    Operation.AddRemoveOperation.Kind.RemoveAll to "removeAll"
                )
            ),

            ;

            val buildCallShortName: String
                get() = buildCallFqName.substringAfterLast('.')
        }

        sealed interface Operation {
            val expression: SmartPsiElementPointer<out KtExpression>

            class AddRemoveOperation(
                override val expression: SmartPsiElementPointer<KtExpression>,
                val kind: Kind,
            ) : Operation {
                enum class Kind {
                    Add, Remove, AddAll, RemoveAll,
                }
            }

            class TransformingOperation(
                override val expression: SmartPsiElementPointer<KtExpression>,
                val kind: Kind,
            ) : Operation {
                enum class Kind(
                    val callFqName: String,
                    val resultCollectionTypeArgumentPosition: ResultCollectionTypeArgumentPosition = ResultCollectionTypeArgumentPosition.Last,
                ) {
                    Map("kotlin.collections.map"),
                    FlatMap("kotlin.collections.flatMap"),
                    MapNotNull("kotlin.collections.mapNotNull"),
                    MapIndex("kotlin.collections.mapIndexed"),
                    Filter("kotlin.collections.filter"),
                    FilterNotNull(
                        "kotlin.collections.filterNotNull",
                        resultCollectionTypeArgumentPosition = ResultCollectionTypeArgumentPosition.First
                    ),
                    FilterNot("kotlin.collections.filterNot"),
                    FilterIsInstance("kotlin.collections.filterIsInstance"),

                    ;

                    val toCallFqName: String = callFqName + "To"
                    val toCallShortName: String = toCallFqName.substringAfterLast(".")

                    enum class ResultCollectionTypeArgumentPosition {
                        First, Last,
                    }

                    companion object {
                        private val callFqNameToKind: Map<String, Kind> =
                            entries.associateBy { it.callFqName }

                        fun byCallFqName(fqName: String?): Kind? {
                            return callFqNameToKind[fqName]
                        }
                    }
                }
            }
        }
    }
}
