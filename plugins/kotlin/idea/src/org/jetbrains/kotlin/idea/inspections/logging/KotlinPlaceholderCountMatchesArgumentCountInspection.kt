// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.logging

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.StandardNames.FqNames.throwable
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.resolveType
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.intentions.receiverType
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinPlaceholderCountMatchesArgumentCountInspection : AbstractKotlinInspection() {

    private enum class LoggerType {
        SLF4J_LOGGER, LOG4J_LOGGER, LOG4J_BUILDER
    }

    companion object {
        private val LOGGING_METHODS = setOf("log", "trace", "debug", "info", "warn", "error", "fatal")
        private val LOGGER_CLASSES =
            mapOf(
                "org.slf4j.Logger" to LoggerType.SLF4J_LOGGER,
                "org.apache.logging.log4j.Logger" to LoggerType.LOG4J_LOGGER,
                "org.apache.logging.log4j.LogBuilder" to LoggerType.LOG4J_BUILDER
            )
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = callExpressionVisitor(fun(call) {
        val name = call.calleeExpression.safeAs<KtSimpleNameExpression>()?.getReferencedName()
        if (name == null || !LOGGING_METHODS.contains(name)) {
            return
        }

        val method = call.resolveToCall()?.resultingDescriptor ?: return
        val parents = method.receiverType().findParents(LOGGER_CLASSES.keys)
        if (parents.isEmpty()) {
            return
        }
        val loggerType = LOGGER_CLASSES[parents.first()] ?: return

        val valueParameters = method.valueParameters
        if (valueParameters.isEmpty()) {
            return
        }
        val patternIndex: Int
        //first case: pattern has index 0
        //example: public void info(String format, Object... arguments);
        if (KotlinBuiltIns.isString(valueParameters[0]?.type)) {
            patternIndex = 0
        } else {
            if (valueParameters.size < 2 || !KotlinBuiltIns.isString(valueParameters[1]?.type)) {
                return
            }
            //second case: pattern has index 1
            //example: public void info(Marker marker, String format, Object... arguments)
            patternIndex = 1
        }

        val valueArguments = call.valueArguments
        val patternArgument = valueArguments.getOrNull(patternIndex) ?: return
        val pattern = patternArgument.getArgumentExpression() ?: return

        var argumentsCount = valueArguments.size - 1 - patternIndex
        var lastArgumentIsException = valueArguments.lastOrNull()?.getArgumentExpression()?.resolveType().isThrowable()

        //consider case like logger.debug("test {} {}", *arrayOf(1, 2, 3)), which can occur when we use auto-conversion from Java
        //other sources of arrayOf is not considered, because they are not popular
        if (argumentsCount == 1) {
            val ktValueOneArgument = valueArguments.getOrNull(patternIndex + 1) ?: return
            val singleArgument = ktValueOneArgument.getArgumentExpression()
            val singleArgumentType = singleArgument?.resolveType()
            if (singleArgumentType != null && KotlinBuiltIns.isArray(singleArgumentType) && ktValueOneArgument.isSpread) {
                val callExpression = singleArgument.safeAs<KtCallExpression>() ?: return
                if (callExpression.resolveToCall()?.resultingDescriptor?.fqNameSafe?.asString() == "kotlin.arrayOf") {
                    val arrayArguments = callExpression.valueArguments
                    argumentsCount = arrayArguments.size
                    lastArgumentIsException =
                        argumentsCount > 0 && arrayArguments.lastOrNull()?.getArgumentExpression()?.resolveType().isThrowable()
                } else {
                    return
                }
            }
        }
        val placeholderCount = countPlaceHolders(pattern, loggerType) ?: return
        checkProblem(loggerType, lastArgumentIsException, argumentsCount, placeholderCount, holder, patternArgument)
    })

    private fun checkProblem(
        loggerType: LoggerType,
        lastArgumentIsException: Boolean,
        argumentsCount: Int,
        placeholderCount: Int,
        holder: ProblemsHolder,
        patternArgument: KtValueArgument
    ) {
        var actualArgumentsCount = argumentsCount
        if (lastArgumentIsException) {
            actualArgumentsCount--
        }
        when (loggerType) {

            LoggerType.SLF4J_LOGGER -> {
                if (placeholderCount == actualArgumentsCount) {
                    return
                }
            }

            LoggerType.LOG4J_BUILDER -> {
                if ((placeholderCount == argumentsCount) ||
                    (placeholderCount == argumentsCount - 1 && lastArgumentIsException)
                ) {
                    return
                }
            }

            // if there is more than one argument and the last argument is an exception, but there is a placeholder for
            // the exception, then the stack trace won't be logged.
            LoggerType.LOG4J_LOGGER -> {
                if ((placeholderCount == argumentsCount && (!lastArgumentIsException || argumentsCount > 1)) ||
                    (placeholderCount == argumentsCount - 1 && lastArgumentIsException)
                ) {
                    return
                }
            }
        }
        registerProblem(placeholderCount, actualArgumentsCount, holder, patternArgument)
    }

    private fun registerProblem(
        placeholderCount: Int,
        argumentsCount: Int,
        holder: ProblemsHolder,
        patternArgument: KtValueArgument
    ) {
        if (placeholderCount < argumentsCount) {
            holder.registerProblem(
                patternArgument,
                KotlinBundle.message("placeholder.count.matches.argument.count.more.problem.descriptor", argumentsCount, placeholderCount)
            )
        } else {
            holder.registerProblem(
                patternArgument,
                KotlinBundle.message("placeholder.count.matches.argument.count.fewer.problem.descriptor", argumentsCount, placeholderCount)
            )
        }
    }

    private fun countPlaceHolders(pattern: KtExpression, loggerType: LoggerType): Int? {
        val bindingContext = pattern.analyze(BodyResolveMode.PARTIAL)
        val constant = ConstantExpressionEvaluator.getConstant(pattern, bindingContext)
            ?.toConstantValue(DefaultBuiltIns.Instance.stringType) ?: return null
        val text = constant.value?.toString() ?: return null
        var count = 0
        var placeHolder = false
        var escaped = false
        for (c in text) {
            if (c == '\\' && loggerType == LoggerType.SLF4J_LOGGER) {
                escaped = !escaped
            } else if (c == '{') {
                if (!escaped) {
                    placeHolder = true
                }
            } else if (c == '}') {
                if (placeHolder) {
                    count++
                }
                placeHolder = false
                escaped = false
            } else {
                placeHolder = false
                escaped = false
            }
        }
        return count
    }
}

private fun KotlinType?.isThrowable(): Boolean {
    return this.isInheritorAny(setOf(throwable.asString()))
}

private fun KotlinType?.isInheritorAny(classes: Set<String>): Boolean {
    return findParents(classes).isNotEmpty()
}

private fun KotlinType?.findParents(classes: Set<String>): Set<String> {
    if (this == null) {
        return emptySet()
    }
    val fqName = this.fqName ?: return emptySet()
    val fqNameAsString = fqName.asString()
    if (classes.contains(fqNameAsString)) {
        return setOf(fqNameAsString)
    }
    return this.supertypes()
        .mapNotNull { it.fqName?.asString() }
        .filter { classes.contains(it) }
        .toSet()
}
