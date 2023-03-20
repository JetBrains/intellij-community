// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.loopToCallChain

import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier
import org.jetbrains.kotlin.resolve.scopes.utils.findFunction
import org.jetbrains.kotlin.resolve.scopes.utils.findPackage
import org.jetbrains.kotlin.resolve.scopes.utils.findVariable

/**
 * Base class for [ResultTransformation]'s that replaces the loop-expression with the result call chain
 */
abstract class ReplaceLoopResultTransformation(final override val loop: KtForExpression) : ResultTransformation {

    override val commentSavingRange = PsiChildRange.singleElement(loop.unwrapIfLabeled())

    override fun generateExpressionToReplaceLoopAndCheckErrors(resultCallChain: KtExpression): KtExpression {
        return resultCallChain
    }

    override fun convertLoop(resultCallChain: KtExpression, commentSavingRangeHolder: CommentSavingRangeHolder): KtExpression {
        return loop.unwrapIfLabeled().replaced(resultCallChain)
    }
}

/**
 * Base class for [ResultTransformation]'s that replaces initialization of a variable with the result call chain
 */
abstract class AssignToVariableResultTransformation(
    final override val loop: KtForExpression,
    protected val initialization: VariableInitialization
) : ResultTransformation {

    override val commentSavingRange = PsiChildRange(initialization.initializationStatement, loop.unwrapIfLabeled())

    override fun generateExpressionToReplaceLoopAndCheckErrors(resultCallChain: KtExpression): KtExpression {
        val psiFactory = KtPsiFactory(resultCallChain.project)
        val initializationStatement = initialization.initializationStatement
        return if (initializationStatement is KtVariableDeclaration) {
            val resolutionScope = loop.getResolutionScope()

            fun isUniqueName(name: String): Boolean {
                val identifier = Name.identifier(name)
                return resolutionScope.findVariable(identifier, NoLookupLocation.FROM_IDE) == null
                        && resolutionScope.findFunction(identifier, NoLookupLocation.FROM_IDE) == null
                        && resolutionScope.findClassifier(identifier, NoLookupLocation.FROM_IDE) == null
                        && resolutionScope.findPackage(identifier) == null
            }

            val uniqueName = Fe10KotlinNameSuggester.suggestNameByName("test", ::isUniqueName)

            val copy = initializationStatement.copied()
            copy.initializer!!.replace(resultCallChain)
            copy.setName(uniqueName)
            copy
        } else {
            psiFactory.createExpressionByPattern("$0 = $1", initialization.variable.nameAsSafeName, resultCallChain, reformat = false)
        }
    }

    override fun convertLoop(resultCallChain: KtExpression, commentSavingRangeHolder: CommentSavingRangeHolder): KtExpression {
        initialization.initializer.replace(resultCallChain)

        val psiFactory = KtPsiFactory(loop.project)

        val variable = initialization.variable
        if (variable.isVar && variable.countWriteUsages() == variable.countWriteUsages(loop)) { // change variable to 'val' if possible
            variable.valOrVarKeyword.replace(psiFactory.createValKeyword())
        }

        val loopUnwrapped = loop.unwrapIfLabeled()

        // move initializer to the loop if needed
        var initializationStatement = initialization.initializationStatement
        if (initializationStatement.nextStatement() != loopUnwrapped) {
            val block = loopUnwrapped.parent
            assert(block is KtBlockExpression)
            val movedInitializationStatement = block.addBefore(initializationStatement, loopUnwrapped) as KtExpression
            block.addBefore(psiFactory.createNewLine(), loopUnwrapped)

            commentSavingRangeHolder.remove(initializationStatement)

            initializationStatement.delete()
            initializationStatement = movedInitializationStatement
        }

        loopUnwrapped.delete()

        return initializationStatement
    }

    companion object {
        fun createDelegated(delegate: ResultTransformation, initialization: VariableInitialization): AssignToVariableResultTransformation {
            return object : AssignToVariableResultTransformation(delegate.loop, initialization) {
                override val presentation: String
                    get() = delegate.presentation

                override fun generateCode(chainedCallGenerator: ChainedCallGenerator): KtExpression {
                    return delegate.generateCode(chainedCallGenerator)
                }

                override val lazyMakesSense: Boolean
                    get() = delegate.lazyMakesSense
            }
        }
    }
}

/**
 * [ResultTransformation] that replaces initialization of a variable with the call chain produced by the given [SequenceTransformation]
 */
class AssignSequenceResultTransformation(
    private val sequenceTransformation: SequenceTransformation,
    initialization: VariableInitialization
) : AssignToVariableResultTransformation(sequenceTransformation.loop, initialization) {

    override val presentation: String
        get() = sequenceTransformation.presentation

    override fun buildPresentation(prevTransformationsPresentation: String?): String {
        return sequenceTransformation.buildPresentation(prevTransformationsPresentation)
    }

    override fun generateCode(chainedCallGenerator: ChainedCallGenerator): KtExpression {
        return sequenceTransformation.generateCode(chainedCallGenerator)
    }
}