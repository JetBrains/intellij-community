// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.idea.codeinsight.utils.createArgumentWithoutName
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

object RemoveArgumentNamesApplicators {
    /**
     * @property anchorArgument an argument after which the unnamed argument should be placed once the argument name is removed;
     * when the argument should be placed in the beginning of argument list, [anchorArgument] is null
     */
    class SingleArgumentInput(
        val anchorArgument: KtValueArgument?,
        val isVararg: Boolean,
        val isArrayOfCall: Boolean
    ) : KotlinApplicatorInput

    /**
     * @property sortedArguments arguments that can be unnamed or already don't have any names;
     * the arguments are sorted by the indices of respective parameters and should be placed in the beginning of argument list
     */
    class MultipleArgumentsInput(
        val sortedArguments: List<KtValueArgument>,
        val vararg: KtValueArgument?,
        val varargIsArrayOfCall: Boolean
    ) : KotlinApplicatorInput

    val singleArgumentsApplicator: KotlinApplicator<KtValueArgument, SingleArgumentInput> = applicator {
        familyAndActionName(KotlinBundle.lazyMessage("remove.argument.name"))

        isApplicableByPsi { valueArgument ->
            if (!valueArgument.isNamed() || valueArgument.getArgumentExpression() == null) return@isApplicableByPsi false
            (valueArgument.parent as? KtValueArgumentList)?.parent is KtCallElement
        }

        applyTo { valueArgument, input ->
            val argumentList = valueArgument.parent as? KtValueArgumentList ?: return@applyTo

            val newArguments = createArgumentWithoutName(valueArgument, input.isVararg, input.isArrayOfCall)
            argumentList.removeArgument(valueArgument)
            newArguments.asReversed().forEach {
                argumentList.addArgumentAfter(it, input.anchorArgument)
            }
        }
    }

    val multipleArgumentsApplicator: KotlinApplicator<KtCallElement, MultipleArgumentsInput> = applicator {
        familyAndActionName(KotlinBundle.lazyMessage("remove.all.argument.names"))

        isApplicableByPsi { callElement ->
            val arguments = callElement.valueArgumentList?.arguments ?: return@isApplicableByPsi false
            arguments.count { it.isNamed() } > 1
        }

        applyTo { callElement, input ->
            val newArguments = input.sortedArguments.flatMap { argument ->
                when (argument) {
                    input.vararg -> createArgumentWithoutName(argument, isVararg = true, input.varargIsArrayOfCall)
                    else -> createArgumentWithoutName(argument)
                }
            }

            val argumentList = callElement.valueArgumentList ?: return@applyTo
            input.sortedArguments.forEach { argumentList.removeArgument(it) }

            newArguments.asReversed().forEach {
                argumentList.addArgumentBefore(it, argumentList.arguments.firstOrNull())
            }
        }
    }
}