// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.psi.isAssignmentLHS
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.AbstractRangeInspection.Companion.rangeExpressionByPsi
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.AbstractRangeInspection.RangeExpression
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.isPrimitiveRangeType
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.ImplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeinsight.utils.LoopToCollectionTransformUtils.findGetCallAccess
import org.jetbrains.kotlin.idea.codeinsight.utils.getImplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeinsight.utils.LoopToCollectionTransformUtils.transformLoop
import org.jetbrains.kotlin.idea.codeinsight.utils.LoopToCollectionTransformUtils.transformLoopWithIndex
import org.jetbrains.kotlin.idea.codeinsight.utils.RangeKtExpressionType
import org.jetbrains.kotlin.idea.codeinsight.utils.RangeKtExpressionType.DOWN_TO
import org.jetbrains.kotlin.idea.codeinsight.utils.RangeKtExpressionType.RANGE_TO
import org.jetbrains.kotlin.idea.codeinsight.utils.RangeKtExpressionType.RANGE_UNTIL
import org.jetbrains.kotlin.idea.codeinsight.utils.RangeKtExpressionType.UNTIL
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.util.match

/**
 * Inspection that suggests replacing manual ranges like `0..<arr.size` with `arr.indices` calls.
 * Extends KotlinApplicableInspectionBase directly (not Simple) to support multiple quick fixes.
 */
class ReplaceManualRangeWithIndicesCallsInspection : KotlinApplicableInspectionBase<KtExpression, ReplaceManualRangeWithIndicesCallsInspection.Context>() {

    /**
     * Describes how the loop index variable is used in the loop body,
     * determining which quick fixes are applicable.
     */
    enum class IndexUsagePattern {
        /**
         * Index is only used for array access: `arr[i]`.
         * Can transform to: `for (element in arr)`
         */
        ELEMENT_LOOP,

        /**
         * Index is used both for array access and other purposes: `println("$i: ${arr[i]}")`.
         * Can transform to: `for ((i, element) in arr.withIndex())`
         */
        WITH_INDEX,

        /**
         * Index is not used for array access, or range is not inside a for loop.
         * Can only replace range with `.indices`: `for (i in arr.indices)`
         */
        INDICES_ONLY
    }

    data class Context(
        val explicitReceiver: KtExpression?,
        val indexUsagePattern: IndexUsagePattern,
        val implicitReceiverInfo: ImplicitReceiverInfo?,
        val suggestedElementName: String?,
        val indexedAccessUsages: List<SmartPsiElementPointer<KtExpression>>,  // Either KtArrayAccessExpression or KtDotQualifiedExpression
    )

    private val RANGE_CALLABLE_NAMES = setOf("until", "rangeTo", "rangeUntil", "downTo")

    private fun getProblemDescription(context: Context): @InspectionMessage String {
        return when (context.indexUsagePattern) {
            IndexUsagePattern.ELEMENT_LOOP -> KotlinBundle.message("for.loop.over.indices.could.be.replaced.with.loop.over.elements")
            IndexUsagePattern.WITH_INDEX -> KotlinBundle.message("for.loop.could.use.withIndex")
            IndexUsagePattern.INDICES_ONLY -> KotlinBundle.message("range.could.be.replaced.with.indices.call")
        }
    }

    override fun isApplicableByPsi(element: KtExpression): Boolean {
        val range = rangeExpressionByPsi(element) ?: return false
        return isApplicableByPsi(range)
    }

    private fun isApplicableByPsi(range: RangeExpression): Boolean {
        // Only ascending ranges are candidates for indices replacement
        if (range.type == DOWN_TO) return false
        
        val (left, right) = range.arguments
        
        // Must start with 0 to be a candidate for indices replacement
        if (left == null || !left.isIntConstantExpression(0)) return false
        
        // Must have a valid target expression that can be extracted
        if (right == null) return false
        
        return extractTargetExpression(range.type, right) != null
    }

    override fun KaSession.prepareContext(element: KtExpression): Context? {
        val range = rangeExpressionByAnalyze(element) ?: return null
        return prepareContextForRange(range)
    }

    /**
     * Returns a [RangeExpression] only if it resolves to a Kotlin stdlib range function.
     * This prevents false positives when custom `until`/`rangeTo` functions are defined.
     */
    private fun KaSession.rangeExpressionByAnalyze(expression: KtExpression): RangeExpression? =
        rangeExpressionByPsi(expression)?.takeIf {
            val callableId = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId
            callableId != null && isStdlibRangeFunction(callableId)
        }

    private fun isStdlibRangeFunction(callableId: CallableId): Boolean {
        val callableName = callableId.callableName.asString()
        if (callableName !in RANGE_CALLABLE_NAMES) return false

        // Extension functions in kotlin.ranges (until, rangeUntil, downTo, and some rangeTo overloads)
        if (callableId.packageName == StandardClassIds.BASE_RANGES_PACKAGE) return true

        // Member functions on primitive types (Int.rangeTo, Int.rangeUntil, etc.)
        if (callableName == "rangeTo" || callableName == "rangeUntil") {
            return callableId.classId?.isPrimitiveRangeType() == true
        }

        return false
    }

    private fun KaSession.prepareContextForRange(range: RangeExpression): Context? {
        val (_, right) = range.arguments

        // Must end with a size/length call
        val sizeCall = right?.let { rightBound(range.type, it) } ?: return null
        val explicitReceiver = (sizeCall as? KtQualifiedExpression)?.receiverExpression

        val indexUsageAnalysis = analyzeIndexUsagePattern(range, explicitReceiver)
        val implicitReceiverInfo = if (explicitReceiver == null) sizeCall.getImplicitReceiverInfo() else null

        // Pre-compute a unique element name for the loop transformation (for ELEMENT_LOOP and WITH_INDEX patterns)
        val suggestedElementName = if (indexUsageAnalysis.pattern != IndexUsagePattern.INDICES_ONLY) {
            val forExpression = findContainingForLoop(range)
            forExpression?.loopParameter?.let { loopParameter ->
                val nameValidator = KotlinDeclarationNameValidator(
                    loopParameter,
                    true,
                    KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
                )
                KotlinNameSuggester.suggestNameByName("element") { nameValidator.validate(it) }
            }
        } else null

        // Store indexed access usages as smart pointers for use in quick fixes
        val indexedAccessPointers = indexUsageAnalysis.indexedAccessUsages.map { it.createSmartPointer() }

        return Context(
            explicitReceiver,
            indexUsageAnalysis.pattern,
            implicitReceiverInfo,
            suggestedElementName,
            indexedAccessPointers
        )
    }

    private fun KaSession.rightBound(type: RangeKtExpressionType, expression: KtExpression): KtExpression? {
        val target = extractTargetExpression(type, expression) ?: return null
        val selector = (target as? KtDotQualifiedExpression)?.selectorExpression ?: target
        val receiverType = resolveReceiverType(target) ?: return null

        // lastIndex is only valid for inclusive ranges (RANGE_TO)
        // For open ranges (UNTIL, RANGE_UNTIL), 0..<lastIndex excludes the last element
        if (selector.text == "lastIndex" && type != RANGE_TO) return null

        return when (selector.text) {
            "size", "lastIndex" -> if (receiverType.isArrayOrPrimitiveArray || receiverType.isSubtypeOf(StandardClassIds.Collection)) target else null
            "length" -> if (receiverType.isSubtypeOf(StandardClassIds.CharSequence)) target else null
            else -> null
        }
    }
    
    private fun extractTargetExpression(type: RangeKtExpressionType, expression: KtExpression): KtExpression? {
        return when (type) {
            UNTIL, RANGE_UNTIL -> expression
            RANGE_TO -> extractFromRangeToExpression(expression)
            DOWN_TO -> null
        }
    }

    private fun extractFromRangeToExpression(expression: KtExpression): KtExpression? {
        // Handle arr.size - 1 pattern
        if (expression is KtBinaryExpression && expression.operationToken == KtTokens.MINUS) {
            val leftOperand = expression.left ?: return null
            val rightOperand = expression.right ?: return null
            return if (rightOperand.isIntConstantExpression(1)) leftOperand else null
        }
        
        // Handle arr.lastIndex pattern
        if (expression is KtDotQualifiedExpression) {
            val selector = expression.selectorExpression
            if (selector?.text == "lastIndex") {
                return expression
            }
        }
        
        return null
    }

    private fun KtExpression.isIntConstantExpression(value: Int): Boolean {
        return (this as? KtConstantExpression)?.text?.toIntOrNull() == value
    }

    private fun KaSession.resolveReceiverType(expression: KtExpression): KaType? {
        val resolvedCall = expression.resolveToCall()
        val variableCall = resolvedCall?.successfulVariableAccessCall() ?: return null
        val partiallyApplied = variableCall.partiallyAppliedSymbol
        
        // For member properties, use dispatchReceiver
        // For extension properties (like lastIndex), use extensionReceiver
        return partiallyApplied.dispatchReceiver?.type ?: partiallyApplied.extensionReceiver?.type
    }

    /**
     * Result of analyzing how the loop index variable is used in the loop body.
     * Supports both bracket notation (`arr[i]`) and `.get()` method calls (`arr.get(i)`).
     */
    private data class IndexUsageAnalysis(
        val pattern: IndexUsagePattern,
        val indexedAccessUsages: List<KtExpression>,  // Either KtArrayAccessExpression or KtDotQualifiedExpression
    )

    /**
     * Analyzes how the loop index variable is used in the loop body.
     *
     * @return IndexUsageAnalysis containing:
     *   - pattern: ELEMENT_LOOP (all indexed accesses), WITH_INDEX (mixed), or INDICES_ONLY (no indexed accesses)
     *   - indexedAccessUsages: list of valid indexed access expressions for transformation
     */
    private fun analyzeIndexUsagePattern(range: RangeExpression, explicitReceiver: KtExpression?): IndexUsageAnalysis {
        val forExpression = findContainingForLoop(range)
            ?: return IndexUsageAnalysis(IndexUsagePattern.INDICES_ONLY, emptyList())
        val loopParam = forExpression.loopParameter?.originalElement
            ?: return IndexUsageAnalysis(IndexUsagePattern.INDICES_ONLY, emptyList())

        val usages = ReferencesSearch.search(loopParam).findAll()
        if (usages.isEmpty()) return IndexUsageAnalysis(IndexUsagePattern.INDICES_ONLY, emptyList())

        val validIndexedAccesses = mutableListOf<KtExpression>()
        var otherUsageCount = 0

        for (reference in usages) {
            val usage = reference.element

            // Check for bracket notation: arr[i]
            val arrayAccess = usage.parents.match(KtContainerNode::class, last = KtArrayAccessExpression::class)
            if (arrayAccess != null && isValidElementLoopCandidate(arrayAccess, explicitReceiver)) {
                validIndexedAccesses.add(arrayAccess)
                continue
            }

            // Check for .get() method call: arr.get(i)
            val getCall = findGetCallAccess(usage)
            if (getCall != null && isValidGetCallCandidate(getCall, explicitReceiver)) {
                validIndexedAccesses.add(getCall)
                continue
            }

            otherUsageCount++
        }

        val pattern = when {
            validIndexedAccesses.isNotEmpty() && otherUsageCount == 0 -> IndexUsagePattern.ELEMENT_LOOP
            validIndexedAccesses.isNotEmpty() && otherUsageCount > 0 -> IndexUsagePattern.WITH_INDEX
            else -> IndexUsagePattern.INDICES_ONLY
        }

        return IndexUsageAnalysis(pattern, validIndexedAccesses)
    }

    private fun findContainingForLoop(range: RangeExpression): KtForExpression? =
        range.expression.parents.match(KtContainerNode::class, last = KtForExpression::class)

    private fun isValidElementLoopCandidate(arrayAccess: KtArrayAccessExpression, explicitReceiver: KtExpression?): Boolean {
        if (arrayAccess.indexExpressions.size != 1) return false
        return receiversMatch(arrayAccess.arrayExpression, explicitReceiver) && !arrayAccess.isAssignmentLHS()
    }

    private fun isValidGetCallCandidate(getCall: KtDotQualifiedExpression, explicitReceiver: KtExpression?): Boolean {
        return receiversMatch(getCall.receiverExpression, explicitReceiver) && !getCall.isAssignmentLHS()
    }

    private fun receiversMatch(accessReceiver: KtExpression?, explicitReceiver: KtExpression?): Boolean {
        if (explicitReceiver == null) return accessReceiver?.safeDeparenthesize() is KtThisExpression
        if (accessReceiver == null) return false

        val access = accessReceiver.safeDeparenthesize()
        val explicit = explicitReceiver.safeDeparenthesize()

        return when {
            access is KtNameReferenceExpression && explicit is KtNameReferenceExpression -> {
                access.mainReference.resolve() == explicit.mainReference.resolve()
            }

            access is KtDotQualifiedExpression && explicit is KtDotQualifiedExpression -> {
                access.selectorExpression?.text == explicit.selectorExpression?.text &&
                        receiversMatch(access.receiverExpression, explicit.receiverExpression)
            }

            access is KtThisExpression && explicit is KtThisExpression -> {
                access.labelQualifier?.text == explicit.labelQualifier?.text
            }

            else -> false
        }
    }

    private fun createQuickFixes(context: Context): List<KotlinModCommandQuickFix<KtExpression>> {
        val indicesFix = ReplaceManualRangeWithIndicesCallQuickFix(context)
        return when (context.indexUsagePattern) {
            IndexUsagePattern.ELEMENT_LOOP -> listOf(ReplaceIndexLoopWithCollectionLoopQuickFix(context), indicesFix)
            IndexUsagePattern.WITH_INDEX -> listOf(ReplaceWithWithIndexLoopQuickFix(context), indicesFix)
            IndexUsagePattern.INDICES_ONLY -> listOf(indicesFix)
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        object : KtVisitorVoid() {
            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }

            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }

    override fun InspectionManager.createProblemDescriptor(
        element: KtExpression,
        context: Context,
        rangeInElement: com.intellij.openapi.util.TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor {
        val fixes = createQuickFixes(context)
        return createProblemDescriptor(
            element,
            rangeInElement,
            getProblemDescription(context),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            onTheFly,
            *fixes.toTypedArray()
        )
    }

    /**
     * Creates an implicit 'this' expression based on the receiver info.
     * For unambiguous receivers, returns simple 'this'. For ambiguous ones, returns 'this@label'.
     */
    private fun createImplicitThis(project: Project, info: ImplicitReceiverInfo): KtThisExpression {
        val thisExpressionText = if (info.isUnambiguousLabel) "this" else "this@${info.receiverLabel?.render()}"
        return KtPsiFactory(project).createExpression(thisExpressionText) as KtThisExpression
    }

    private inner class ReplaceManualRangeWithIndicesCallQuickFix(
        private val context: Context
    ) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.manual.range.with.indices.call.quick.fix.text")

        override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
            val explicitReceiver = context.explicitReceiver
            val newReceiver = when {
                explicitReceiver is KtThisExpression && explicitReceiver.labelQualifier == null -> null
                explicitReceiver != null -> explicitReceiver
                else -> context.implicitReceiverInfo?.takeUnless { it.isUnambiguousLabel }?.let { createImplicitThis(project, it) }
            }
            val psiFactory = KtPsiFactory(project)
            val newExpression = if (newReceiver != null) {
                psiFactory.createExpressionByPattern("$0.indices", newReceiver)
            } else {
                psiFactory.createExpression("indices")
            }
            val replaced: KtExpression = element.replaced(newExpression)
            replaced.removeUnnecessaryParentheses()
        }

        private fun KtExpression.removeUnnecessaryParentheses() {
            parents.takeWhile { it is KtParenthesizedExpression }.lastOrNull()?.let {
                if (it is KtParenthesizedExpression && KtPsiUtil.areParenthesesUseless(it)) {
                    it.replace(it.safeDeparenthesize())
                }
            }
        }
    }

    private inner class ReplaceIndexLoopWithCollectionLoopQuickFix(
        private val context: Context,
    ) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.index.loop.with.collection.loop.quick.fix.text")

        override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
            val forExpression = element.getStrictParentOfType<KtForExpression>() ?: return
            val loopParameter = forExpression.loopParameter ?: return
            val loopRange = forExpression.loopRange ?: return
            val elementName = context.suggestedElementName ?: return

            // Get writable indexed accesses from smart pointers
            val indexedAccesses = context.indexedAccessUsages.mapNotNull { pointer ->
                pointer.element?.let { updater.getWritable(it) }
            }
            if (indexedAccesses.isEmpty()) return

            val collection = resolveCollectionExpression(project)
            transformLoop(project, indexedAccesses, loopParameter, loopRange, collection, elementName)
        }

        /**
         * Resolves the collection expression for the loop transformation.
         * Handles both explicit receivers (arr.size -> arr) and implicit ones (size -> this).
         */
        private fun resolveCollectionExpression(project: Project): KtExpression {
            // Use explicit receiver if available
            context.explicitReceiver?.let { return it }

            // For implicit receivers, only add explicit 'this@label' for ambiguous cases
            context.implicitReceiverInfo?.takeUnless { it.isUnambiguousLabel }?.let {
                return createImplicitThis(project, it)
            }

            // Default to simple 'this' for unambiguous cases
            return KtPsiFactory(project).createThisExpression()
        }
    }


    private inner class ReplaceWithWithIndexLoopQuickFix(
        private val context: Context,
    ) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.index.loop.with.withIndex.quick.fix.text")

        override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
            val forExpression = element.getStrictParentOfType<KtForExpression>() ?: return
            val loopParameter = forExpression.loopParameter ?: return
            val loopRange = forExpression.loopRange ?: return
            val elementName = context.suggestedElementName ?: return

            // Get writable indexed accesses from smart pointers
            val indexedAccesses = context.indexedAccessUsages.mapNotNull { pointer ->
                pointer.element?.let { updater.getWritable(it) }
            }
            if (indexedAccesses.isEmpty()) return

            val collection = resolveCollectionExpression(project)
            transformLoopWithIndex(
                project, indexedAccesses, loopParameter, loopRange, collection, elementName
            )
        }

        private fun resolveCollectionExpression(project: Project): KtExpression {
            context.explicitReceiver?.let { return it }

            context.implicitReceiverInfo?.takeUnless { it.isUnambiguousLabel }?.let {
                return createImplicitThis(project, it)
            }

            return KtPsiFactory(project).createThisExpression()
        }
    }
}