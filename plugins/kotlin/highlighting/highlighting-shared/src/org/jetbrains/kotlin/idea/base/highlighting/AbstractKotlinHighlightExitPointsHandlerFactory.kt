// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.impl.FindManagerImpl
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiRecursiveVisitor
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.Consumer
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.doesBelongToLoop
import org.jetbrains.kotlin.idea.codeinsight.utils.findRelevantLoopForExpression
import org.jetbrains.kotlin.idea.references.unwrappedTargets
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addIfNotNull

abstract class AbstractKotlinHighlightExitPointsHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    private fun getOnReturnOrThrowOrLambdaUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        val expression = when (val parent = target.parent) {
            is KtNamedFunction -> parent.takeIf { (target as? ASTNode)?.elementType == KtTokens.FUN_KEYWORD }
            is KtPropertyAccessor -> parent
            is KtReturnExpression, is KtThrowExpression -> parent
            is KtFunctionLiteral -> parent.takeIf { with((target as? ASTNode)?.elementType) { this == KtTokens.LBRACE || this == KtTokens.RBRACE } }
            is KtLabelReferenceExpression -> PsiTreeUtil.getParentOfType(
                target, KtReturnExpression::class.java, KtThrowExpression::class.java, KtFunction::class.java
            )?.takeUnless {
                it is KtFunction
            }

            else -> null
        } ?: return null
        return OnExitUsagesHandler(editor, file, null, expression, false)
    }

    private fun getOnBreakOrContinueUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        val expression = when (val parent = target.parent) {
            is KtBreakExpression, is KtContinueExpression -> parent
            is KtDoWhileExpression -> parent.takeIf { target.elementType == KtTokens.DO_KEYWORD }
            is KtLoopExpression -> parent
            else -> null
        } as? KtExpression ?: return null
        return OnLoopUsagesHandler(editor, file, expression)
    }

    private fun getOnGeneratorUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        val expression = when (val parent = target.parent) {
            is KtNameReferenceExpression -> parent.takeIf {
                target.elementType == KtTokens.IDENTIFIER && parent.text in BUILDER_KEYWORDS
            }?.let { it.parent as? KtCallExpression }

            else -> null
        } as? KtExpression ?: return null

        return OnGeneratorUsagesHandler(editor, file, expression)
    }

    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        val handler = getOnReturnOrThrowOrLambdaUsageHandler(editor, file, target)
            ?: getOnBreakOrContinueUsageHandler(editor, file, target)
        if (handler != null) return handler

        return if (Registry.`is`("kotlin.highlight.stdlib.dsl.exit.points")) {
            getOnGeneratorUsageHandler(editor, file, target)
        } else{
            null
        }
    }

    protected abstract fun getRelevantReturnDeclaration(returnExpression: KtReturnExpression): KtDeclarationWithBody?

    protected abstract fun isInlinedArgument(declaration: KtDeclarationWithBody): Boolean

    protected abstract fun hasNonUnitReturnType(functionLiteral: KtFunctionLiteral): Boolean

    protected fun getRelevantDeclaration(expression: KtExpression): KtDeclarationWithBody? {
        if (expression is KtReturnExpression) {
            getRelevantReturnDeclaration(expression)?.let { return it }
        }

        if (expression is KtThrowExpression || expression is KtReturnExpression) {
            for (parent in expression.parents) {
                if (parent is KtDeclarationWithBody) {
                    if (parent is KtPropertyAccessor) {
                        return parent
                    }

                    if ((parent is KtFunctionLiteral || parent is KtNamedFunction) && !isInlinedArgument(parent)) {
                        return parent
                    }
                }
            }

            return null
        }

        return expression.parents.filterIsInstance<KtDeclarationWithBody>().firstOrNull()
    }

    private inner class OnExitUsagesHandler(
        editor: Editor,
        file: PsiFile,
        val referenceExpression: KtNameReferenceExpression?,
        val target: KtExpression,
        val highlightReferences: Boolean
    ) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {

        override fun getTargets(): List<KtExpression> = listOf(target)

        override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
            selectionConsumer.consume(targets)
        }

        override fun computeUsages(targets: List<PsiElement>) {
            val relevantFunction: KtDeclarationWithBody? = when (target) {
                is KtFunctionLiteral -> target
                is KtPropertyAccessor -> target
                is KtNamedFunction -> target
                else -> getRelevantDeclaration(target)
            }

            var targetOccurrenceAdded = false
            if (target is KtReturnExpression || target is KtThrowExpression || target is KtNamedFunction || target is KtPropertyAccessor) {
                when (relevantFunction) {
                    is KtNamedFunction -> relevantFunction.funKeyword?.let {
                        targetOccurrenceAdded = true
                        addOccurrence(it)
                    }

                    is KtPropertyAccessor -> relevantFunction.namePlaceholder.let {
                        targetOccurrenceAdded = true
                        addOccurrence(it)
                    }

                    is KtFunctionLiteral -> {
                        targetOccurrenceAdded = true
                        addOccurrence(relevantFunction.lBrace)
                        relevantFunction.rBrace?.let(::addOccurrence)
                    }
                }
            }

            val lastStatementExpressions =
                if ((relevantFunction is KtFunctionLiteral && hasNonUnitReturnType(relevantFunction)) || (relevantFunction is KtNamedFunction && relevantFunction.bodyBlockExpression == null)) {
                    val lastStatements = mutableSetOf<PsiElement>(relevantFunction)
                    relevantFunction.acceptChildren(object : KtVisitorVoid(), PsiRecursiveVisitor {
                        override fun visitKtElement(element: KtElement) {
                            ProgressIndicatorProvider.checkCanceled()
                            element.acceptChildren(this)
                        }

                        override fun visitExpression(expression: KtExpression) {
                            when (expression) {
                                is KtBinaryExpression -> {
                                    expression.left?.let {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                    expression.right?.let {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                }

                                is KtCallExpression -> {
                                    expression.calleeExpression?.let {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                }

                                is KtBlockExpression -> {
                                    expression.lastStatementOrNull()?.let {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                }

                                is KtIfExpression -> {
                                    expression.then?.let {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                    expression.`else`?.let {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                }

                                is KtWhenExpression -> {
                                    expression.entries.mapNotNull { it.expression }.forEach {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                }

                                else -> super.visitExpression(expression)
                            }
                        }
                    })
                    if (target !is KtReturnExpression && target !is KtThrowExpression && target !in lastStatements) {
                        return
                    }
                    lastStatements
                } else {
                    emptySet()
                }

            relevantFunction?.accept(object : KtVisitorVoid(), PsiRecursiveVisitor {
                override fun visitKtElement(element: KtElement) {
                    ProgressIndicatorProvider.checkCanceled()
                    element.acceptChildren(this)
                }

                override fun visitExpression(expression: KtExpression) {
                    if (relevantFunction is KtFunctionLiteral || relevantFunction is KtNamedFunction) {
                        if (occurrenceForFunctionLiteralReturnExpression(expression, expression in lastStatementExpressions)) {
                            addTargetOccurenceIfNeeded(relevantFunction)
                            return
                        }
                    }

                    super.visitExpression(expression)
                }

                private fun addTargetOccurenceIfNeeded(relevantFunction: KtDeclarationWithBody) {
                    if (!targetOccurrenceAdded) {
                        when (relevantFunction) {
                            is KtNamedFunction -> relevantFunction.funKeyword
                            else -> null
                        }?.let {
                            targetOccurrenceAdded = true
                            addOccurrence(it)
                        }
                    }
                }

                private fun occurrenceForFunctionLiteralReturnExpression(expression: KtExpression, lastLambdaExpression: Boolean): Boolean {
                    if (!KtPsiUtil.isStatement(expression)) return false

                    if (expression is KtIfExpression || expression is KtWhenExpression || expression is KtBlockExpression) {
                        return false
                    }

                    if (!lastLambdaExpression) {
                        return false
                    }

                    if (getRelevantDeclaration(expression) != relevantFunction) {
                        return false
                    }

                    addOccurrence(expression)
                    return true
                }

                private fun isRelevantFunction(expression: KtExpression): Boolean = getRelevantDeclaration(expression) == relevantFunction

                override fun visitReturnExpression(expression: KtReturnExpression) {
                    if (!isRelevantFunction(expression)) return

                    when (expression.returnedExpression) {
                        is KtIfExpression, is KtWhenExpression, is KtTryExpression -> {
                            addOccurrence(expression.returnKeyword)
                            expression.acceptChildren(object : KtVisitorVoid(), PsiRecursiveVisitor {
                                override fun visitKtElement(element: KtElement) {
                                    ProgressIndicatorProvider.checkCanceled()
                                    element.acceptChildren(this)
                                }

                                override fun visitExpression(expression: KtExpression) {
                                    when (expression) {
                                        is KtBlockExpression -> expression.lastStatementOrNull()?.let { visitExpression(it) }
                                        is KtIfExpression -> {
                                            expression.then?.let { visitExpression(it) }
                                            expression.`else`?.let { visitExpression(it) }
                                        }

                                        is KtTryExpression -> {
                                            expression.tryBlock.lastStatementOrNull()?.let { visitExpression(it) }
                                            expression.catchClauses.forEach { catchClause ->
                                                catchClause.catchBody?.let { visitExpression(it) }
                                            }
                                        }

                                        is KtWhenExpression -> expression.entries.forEach { whenEntry ->
                                            whenEntry.expression?.let { visitExpression(it) }
                                        }

                                        else -> addOccurrence(expression)
                                    }
                                }
                            })
                        }

                        else -> addOccurrence(expression)
                    }

                    addTargetOccurenceIfNeeded(relevantFunction)
                }

                override fun visitThrowExpression(expression: KtThrowExpression) {
                    if (!isRelevantFunction(expression)) return

                    addOccurrence(expression)
                }
            })

            if (relevantFunction != null) {
                val containingFile = relevantFunction.containingFile
                referenceExpression?.reference?.unwrappedTargets?.firstOrNull()?.let { target ->
                    (target as? PsiNameIdentifierOwner)?.nameIdentifier.takeIf { target.containingFile == containingFile }
                        ?.let(::addOccurrence)

                    val handler: FindUsagesHandler? =
                        (FindManager.getInstance(relevantFunction.project) as FindManagerImpl).findUsagesManager.getFindUsagesHandler(
                            target,
                            true
                        )
                    handler?.findReferencesToHighlight(target, LocalSearchScope(containingFile)).let { ref ->
                        ref?.forEach { addOccurrence(it.element) }
                    }
                }
            }
        }

        override fun highlightReferences(): Boolean = highlightReferences
    }

    private class OnLoopUsagesHandler(editor: Editor, file: PsiFile, val target: KtExpression) :
        HighlightUsagesHandlerBase<PsiElement>(editor, file) {
        override fun getTargets(): List<KtExpression> = listOf(target)

        override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
            selectionConsumer.consume(targets)
        }

        override fun computeUsages(targets: MutableList<out PsiElement>) {
            val relevantLoop = findRelevantLoopForExpression(target) ?: return
            val loopLabelName = (relevantLoop.parent as? KtLabeledExpression)?.getLabelName()

            when (relevantLoop) {
                is KtForExpression -> addOccurrence(relevantLoop.forKeyword)
                is KtDoWhileExpression -> relevantLoop.node.findChildByType(KtTokens.DO_KEYWORD)?.psi?.let(::addOccurrence)
                is KtWhileExpression -> relevantLoop.node.findChildByType(KtTokens.WHILE_KEYWORD)?.psi?.let(::addOccurrence)
            }

            relevantLoop.accept(object : KtVisitorVoid(), PsiRecursiveVisitor {
                override fun visitKtElement(element: KtElement) {
                    ProgressIndicatorProvider.checkCanceled()
                    element.acceptChildren(this)
                }

                override fun visitExpression(expression: KtExpression) {
                    when (expression) {
                        is KtBlockExpression -> {
                            val containerNode = expression.parent as? KtContainerNode
                            val loopExpression = containerNode?.parent as? KtLoopExpression

                            if (loopExpression != null && loopExpression != relevantLoop) {
                                val nestedLoopLabelName = (loopExpression.parent as? KtLabeledExpression)?.getLabelName()
                                // no reasons to step into another loop with the same label name or no label name
                                if (loopLabelName == nestedLoopLabelName) return
                                if (loopLabelName == null) return
                            }
                        }

                        is KtBreakExpression, is KtContinueExpression -> {
                            val expressionLabelName = (expression as? KtExpressionWithLabel)?.getLabelName()
                            if (expressionLabelName != null && expressionLabelName == loopLabelName) {
                                addOccurrence(expression)
                            } else {
                                if (expressionLabelName == null && expression.doesBelongToLoop(relevantLoop)) {
                                    addOccurrence(expression)
                                }
                            }
                        }
                    }

                    super.visitExpression(expression)
                }
            })
        }

    }

    private inner class OnGeneratorUsagesHandler(editor: Editor, file: PsiFile, val target: KtExpression) :
        HighlightUsagesHandlerBase<PsiElement>(editor, file) {

        override fun getTargets(): List<KtExpression> = listOf(target)

        override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
            selectionConsumer.consume(targets)
        }

        override fun computeUsages(targets: List<PsiElement>) {
            val (generatorCall, builderPoints) = findGeneratorCall(target) ?: return
            // Handles both trailing lambda syntax: sequence { } and parenthesized: sequence({ })
            val generatorLambda =
                generatorCall.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: (generatorCall.valueArguments.firstOrNull()
                    ?.getArgumentExpression() as? KtLambdaExpression) ?: return

            // Highlight "sequence" keyword
            generatorCall.calleeExpression?.let { addOccurrence(it) }

            // Find all yield/yieldAll for current sequence lambda
            generatorLambda.accept(object : KtVisitorVoid(), PsiRecursiveVisitor {
                override fun visitKtElement(element: KtElement) {
                    element.acceptChildren(this)
                }

                override fun visitCallExpression(expression: KtCallExpression) {
                    super.visitCallExpression(expression)
                    // Check if this call belongs to our sequence (not nested)
                    if (belongsToBuilder(expression, generatorLambda, builderPoints) && isBuilderPointCall(expression, builderPoints)) {
                        expression.calleeExpression?.let { addOccurrence(it) }
                    }
                }
            })
        }
    }

    private fun findGeneratorCall(expression: KtExpression): Pair<KtCallExpression, BuilderPoint>? {
        // If clicked the generator keyword
        if (expression is KtCallExpression) {
            findMatchingBuilderPoints(expression)?.let { return expression to it }
        }

        // Clicked exitPoints methods -> Find enclosing call, exitPoints
        return expression.parents.filterIsInstance<KtLambdaExpression>().firstNotNullOfOrNull { lambdaExpr ->
            val lambdaArg = lambdaExpr.parent
            val call = when (lambdaArg) {
                is KtLambdaArgument -> lambdaArg.parent as? KtCallExpression
                is KtValueArgument -> (lambdaArg.parent as? KtValueArgumentList)?.parent as? KtCallExpression
                else -> null
            }
            call?.let { findMatchingBuilderPoints(it)?.let { builderPoints -> call to builderPoints } }
        }
    }

    private fun belongsToBuilder(
        builderPointCall: KtCallExpression,
        targetSequenceLambda: KtLambdaExpression,
        targetBuilderPoint: BuilderPoint
    ): Boolean {
        // Check if exit point doesn't belong to a nested sequence
        for (parent in builderPointCall.parents) {
            if (parent == targetSequenceLambda) return true

            if (parent is KtLambdaExpression && parent != targetSequenceLambda) {
                val call = when (val lambdaParent = parent.parent) {
                    is KtLambdaArgument -> lambdaParent.parent as? KtCallExpression  // sequence { }
                    is KtValueArgument -> (lambdaParent.parent as? KtValueArgumentList)?.parent as? KtCallExpression  // sequence({ })
                    else -> null
                }
                val nestedBuilderPoints = call?.let { findMatchingBuilderPoints(it) }
                if (nestedBuilderPoints == targetBuilderPoint) {
                    return false
                }
            }

        }
        return false
    }

    private fun findMatchingBuilderPoints(call: KtCallExpression): BuilderPoint? {
        return generatorPairs.entries.firstNotNullOfOrNull { (generatorCallableId, builderPoints) ->
            builderPoints.takeIf { call.resolvesToCallableId(generatorCallableId) }
        }
    }

    private fun isBuilderPointCall(call: KtCallExpression, builderPoint: BuilderPoint): Boolean {
        val allBuilderPoints = builderPoint.single + builderPoint.multiple
        return call.resolvesToCallableId(*allBuilderPoints.toTypedArray())
    }

    private fun MutableSet<PsiElement>.addIfNotNullAndNotBlock(element: PsiElement?) {
        addIfNotNull(element.takeUnless { it is KtBlockExpression })
    }

    private fun KtBlockExpression.lastStatementOrNull(): KtExpression? {
        var expression: KtExpression? = null
        var cur = getFirstChild()
        while (cur != null) {
            (cur as? KtExpression)?.let { expression = it }
            cur = cur.getNextSibling()
        }
        return expression
    }

    private fun KtCallExpression.resolvesToCallableId(vararg expectedCallableIds: CallableId): Boolean {
        analyze(this) {
            val resolvedCall = resolveToCall()?.successfulFunctionCallOrNull() ?: return false
            val symbol = resolvedCall.partiallyAppliedSymbol.signature.symbol
            val callableId = symbol.callableId ?: return false
            return callableId in expectedCallableIds
        }
    }

    companion object {
        private val BUILDER_KEYWORDS = setOf(
            "yield", "yieldAll", "sequence",
            "emit", "emitAll", "flow",
            "add", "addAll", "buildList", "buildSet",
            "put", "putAll", "buildMap",
            "append", "appendAll", "buildString"
        )
    }

    data class BuilderPoint(
        val single: List<CallableId>,
        val multiple: List<CallableId>
    )

    val generatorPairs: Map<CallableId, BuilderPoint> = mapOf(
        StandardKotlinNames.Sequences.sequence to BuilderPoint(
            single = listOf(StandardKotlinNames.Sequences.yield),
            multiple = listOf(StandardKotlinNames.Sequences.yieldAll)
        ),
        StandardKotlinNames.BuildScope.buildList to BuilderPoint(
            single = listOf(
                StandardKotlinNames.BuildScope.addList,
                StandardKotlinNames.BuildScope.addCollection
            ),
            multiple = listOf(
                StandardKotlinNames.BuildScope.addAllList,
                StandardKotlinNames.BuildScope.addAllCollection,
                StandardKotlinNames.BuildScope.addAllExtension
            )
        ),
        StandardKotlinNames.BuildScope.buildSet to BuilderPoint(
            single = listOf(
                StandardKotlinNames.BuildScope.addSet,
                StandardKotlinNames.BuildScope.addCollection
            ),
            multiple = listOf(
                StandardKotlinNames.BuildScope.addAllSet,
                StandardKotlinNames.BuildScope.addAllCollection,
                StandardKotlinNames.BuildScope.addAllExtension
            )
        ),
        StandardKotlinNames.BuildScope.buildMap to BuilderPoint(
            single = listOf(
                StandardKotlinNames.BuildScope.put
            ),
            multiple = listOf(StandardKotlinNames.BuildScope.putAll)
        ),
        StandardKotlinNames.BuildScope.buildString to BuilderPoint(
            single = listOf(
                StandardKotlinNames.BuildScope.append,
                StandardKotlinNames.BuildScope.appendRange
            ),
            multiple = listOf(StandardKotlinNames.BuildScope.appendLine)
        ),
        StandardKotlinNames.Flow.flow to BuilderPoint(
            single = listOf(StandardKotlinNames.Flow.emit),
            multiple = listOf(StandardKotlinNames.Flow.emitAll)
        ),
    )

}