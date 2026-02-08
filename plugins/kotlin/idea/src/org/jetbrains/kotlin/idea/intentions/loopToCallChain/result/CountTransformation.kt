// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.loopToCallChain.result

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.AssignToVariableResultTransformation
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.ChainedCallGenerator
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.MatchingState
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.ResultTransformation
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.SequenceTransformation
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.TransformationMatch
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.TransformationMatcher
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.VariableInitialization
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.countUsages
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.findVariableInitializationBeforeLoop
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.generateLambda
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.isPlusPlusOf
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.isZeroConstant
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.sequence.FilterTransformationBase
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

@K1Deprecation
class CountTransformation(
    loop: KtForExpression,
    private val inputVariable: KtCallableDeclaration,
    initialization: VariableInitialization,
    private val filter: KtExpression?
) : AssignToVariableResultTransformation(loop, initialization) {

    override fun mergeWithPrevious(previousTransformation: SequenceTransformation, reformat: Boolean): ResultTransformation? {
        if (previousTransformation !is FilterTransformationBase) return null
        if (previousTransformation.indexVariable != null) return null
        val newFilter = if (filter == null)
            previousTransformation.effectiveCondition.asExpression(reformat)
        else
            KtPsiFactory(filter.project).createExpressionByPattern(
                "$0 && $1", previousTransformation.effectiveCondition.asExpression(reformat), filter,
                reformat = reformat
            )
        return CountTransformation(loop, previousTransformation.inputVariable, initialization, newFilter)
    }

    override val presentation: String
        get() = "count" + (if (filter != null) "{}" else "()")

    override fun generateCode(chainedCallGenerator: ChainedCallGenerator): KtExpression {
        val reformat = chainedCallGenerator.reformat
        val call = if (filter != null) {
            val lambda = generateLambda(inputVariable, filter, reformat)
            chainedCallGenerator.generate("count $0:'{}'", lambda)
        } else {
            chainedCallGenerator.generate("count()")
        }

        return if (initialization.initializer.isZeroConstant()) {
            call
        } else {
            KtPsiFactory(call.project).createExpressionByPattern("$0 + $1", initialization.initializer, call, reformat = reformat)
        }
    }

    /**
     * Matches:
     *     val variable = 0
     *     for (...) {
     *         ...
     *         variable++ (or ++variable)
     *     }
     */
    object Matcher : TransformationMatcher {
        override val indexVariableAllowed: Boolean
            get() = false

        override val shouldUseInputVariables: Boolean
            get() = false

        override fun match(state: MatchingState): TransformationMatch.Result? {
            val operand = state.statements.singleOrNull()?.isPlusPlusOf() ?: return null
            val initialization =
                operand.findVariableInitializationBeforeLoop(state.outerLoop, checkNoOtherUsagesInLoop = true) ?: return null

            // this should be the only usage of this variable inside the loop
            if (initialization.variable.countUsages(state.outerLoop) != 1) return null

            val variableType = initialization.variable.resolveToDescriptorIfAny()?.type ?: return null
            if (!KotlinBuiltIns.isInt(variableType)) return null

            val transformation = CountTransformation(state.outerLoop, state.inputVariable, initialization, null)
            return TransformationMatch.Result(transformation)
        }
    }
}