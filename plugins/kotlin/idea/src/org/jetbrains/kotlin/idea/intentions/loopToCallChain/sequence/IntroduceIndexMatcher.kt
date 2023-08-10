// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.loopToCallChain.sequence

import org.jetbrains.kotlin.cfg.pseudocode.instructions.Instruction
import org.jetbrains.kotlin.cfg.pseudocode.instructions.eval.AccessTarget
import org.jetbrains.kotlin.cfg.pseudocode.instructions.eval.ReadValueInstruction
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.TraverseInstructionResult
import org.jetbrains.kotlin.cfg.pseudocodeTraverser.traverseFollowingInstructions
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

/**
 * Analyzes the rest of the loop and detects index variable manually incremented inside.
 * So it does not produce any transformation in its result but adds an index variable.
 */
object IntroduceIndexMatcher : TransformationMatcher {
    override val indexVariableAllowed: Boolean
        get() = false // old index variable is still needed - cannot introduce another one

    override val shouldUseInputVariables: Boolean
        get() = false

    override fun match(state: MatchingState): TransformationMatch.Sequence? {
        for (statement in state.statements) {
            val unaryExpressions = statement.collectDescendantsOfType<KtUnaryExpression>(
                canGoInside = { it !is KtBlockExpression && it !is KtFunction }
            )
            for (unaryExpression in unaryExpressions) {
                checkIndexCandidate(unaryExpression, state)?.let { return it }
            }
        }
        return null
    }

    private fun checkIndexCandidate(incrementExpression: KtUnaryExpression, state: MatchingState): TransformationMatch.Sequence? {
        val operand = incrementExpression.isPlusPlusOf() ?: return null

        val variableInitialization = operand.findVariableInitializationBeforeLoop(state.outerLoop, checkNoOtherUsagesInLoop = false)
            ?: return null
        if ((variableInitialization.initializer as? KtConstantExpression)?.text != "0") return null

        val variable = variableInitialization.variable

        if (variable.countWriteUsages(state.outerLoop) > 1) return null // changed somewhere else

        // variable should have no usages except in the initialization + currently matching part of the loop
        //TODO: preform more precise analysis when variable can be used earlier or used later but value overwritten before that
        if (variable.countUsages() != variable.countUsages(state.statements + variableInitialization.initializationStatement)) return null

        val pseudocode = state.pseudocodeProvider()
        val firstStatement = state.statements.first()
        val firstInstruction = pseudocode.instructionForElement(firstStatement) ?: return null
        val incrementInstruction = pseudocode.instructionForElement(incrementExpression) ?: return null
        if (!isAlwaysReachedOrExitedLoop(firstInstruction, incrementInstruction, state.outerLoop, state.innerLoop)) return null

        val variableDescriptor = variable.unsafeResolveToDescriptor() as VariableDescriptor
        // index accessed inside loop after increment
        if (isAccessedAfter(variableDescriptor, incrementInstruction, state.innerLoop)) return null

        // if it is among statements then drop it, otherwise "index++" will be replaced with "index" by generateLambda()
        val restStatements = state.statements - incrementExpression
        val newState = state.copy(
            statements = restStatements,
            indexVariable = variable,
            initializationStatementsToDelete = state.initializationStatementsToDelete + variableInitialization.initializationStatement,
            incrementExpressions = state.incrementExpressions + incrementExpression
        )
        return TransformationMatch.Sequence(emptyList(), newState)
    }

    private fun isAlwaysReachedOrExitedLoop(
        from: Instruction,
        to: Instruction,
        outerLoop: KtForExpression,
        innerLoop: KtForExpression
    ): Boolean {
        val visited = HashSet<Instruction>()
        return traverseFollowingInstructions(from, visited) { instruction ->
            val nextInstructionScope = instruction.blockScope.block
            // we should either reach the target instruction or exit the outer loop on every branch
            // (if we won't do this on some branch we will finally exit the inner loop and return false from traverseFollowingInstructions)
            when {
                instruction == to -> TraverseInstructionResult.SKIP
                // we are out of the outer loop - it's ok
                !outerLoop.isAncestor(nextInstructionScope, strict = false) -> TraverseInstructionResult.SKIP
                // we exited or continued inner loop
                !innerLoop.isAncestor(nextInstructionScope, strict = true) -> TraverseInstructionResult.HALT
                else -> TraverseInstructionResult.CONTINUE
            }
        } && visited.contains(to)
    }

    private fun isAccessedAfter(variableDescriptor: VariableDescriptor, instruction: Instruction, loop: KtForExpression): Boolean {
        return !traverseFollowingInstructions(instruction) {
            when {
                !loop.isAncestor(
                    it.blockScope.block,
                    strict = true
                ) -> TraverseInstructionResult.SKIP // we are outside the loop or going to the next iteration
                it.isReadOfVariable(variableDescriptor) -> TraverseInstructionResult.HALT
                else -> TraverseInstructionResult.CONTINUE
            }
        }
    }

    private fun Instruction.isReadOfVariable(descriptor: VariableDescriptor): Boolean {
        return ((this as? ReadValueInstruction)?.target as? AccessTarget.Call)?.resolvedCall?.resultingDescriptor == descriptor
    }
}