// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.psi.*

// TODO merge with ReplaceJavaStaticMethodWithKotlinAnalogInspection
class JavaCollectionsStaticMethodInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return dotQualifiedExpressionVisitor(fun(expression) {
            val (methodName, firstArg) = getTargetMethodOnMutableList(expression) ?: return
            holder.registerProblem(
                expression,
                KotlinBundle.message("java.collections.static.method.call.should.be.replaced.with.kotlin.stdlib"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                ReplaceWithStdLibFix(methodName, firstArg.text)
            )
        })
    }

    object Util {
        fun getTargetMethodOnImmutableList(expression: KtDotQualifiedExpression): Pair<String, KtValueArgument>? =
            getTargetMethod(expression) { session, type -> isListOrSubtype(type, session) && !isMutableListOrSubtype(type, session) }
    }
}

private fun getTargetMethodOnMutableList(expression: KtDotQualifiedExpression) =
    getTargetMethod(expression) { session, type -> isMutableListOrSubtype(type, session) }

private fun getTargetMethod(
    expression: KtDotQualifiedExpression,
    isValidFirstArgument: (KaSession, KaType?) -> Boolean
): Pair<String, KtValueArgument>? {
    val callExpression = expression.callExpression ?: return null
    val args = callExpression.valueArguments
    val firstArg = args.firstOrNull() ?: return null
    val fqName: String = analyze(expression) {
        val firstArgType = firstArg.getArgumentExpression()?.expressionType
        if (!isValidFirstArgument(this, firstArgType)) return@analyze null
        val call = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return@analyze null
        val callableId = call.partiallyAppliedSymbol.symbol.callableId ?: return@analyze null
        callableId.asSingleFqName().asString()
    } ?: return null
    if (!canReplaceWithStdLib(expression, fqName, args)) return null

    val methodName = fqName.split(".").last()
    return methodName to firstArg
}

private fun canReplaceWithStdLib(expression: KtDotQualifiedExpression, fqName: String, args: List<KtValueArgument>): Boolean {
    if (!fqName.startsWith("java.util.Collections.")) return false
    val size = args.size
    return when (fqName) {
        "java.util.Collections.fill" -> checkApiVersion(ApiVersion.KOTLIN_1_2, expression) && size == 2
        "java.util.Collections.reverse" -> size == 1
        "java.util.Collections.shuffle" -> checkApiVersion(ApiVersion.KOTLIN_1_2, expression) && (size == 1 || size == 2)
        "java.util.Collections.sort" -> {
            size == 1 || (size == 2 && args.getOrNull(1)?.getArgumentExpression() is KtLambdaExpression)
        }

        else -> false
    }
}

private fun checkApiVersion(requiredVersion: ApiVersion, expression: KtDotQualifiedExpression): Boolean {
    val module = ModuleUtilCore.findModuleForPsiElement(expression) ?: return true
    return module.languageVersionSettings.apiVersion >= requiredVersion
}

private fun isMutableListOrSubtype(type: KaType?, session: KaSession): Boolean {
    return isListOrSubtype(type, isMutable = true, session)
}

private fun isListOrSubtype(type: KaType?, session: KaSession): Boolean {
    return isListOrSubtype(type, isMutable = false, session)
}

private fun isListOrSubtype(type: KaType?, isMutable: Boolean, session: KaSession): Boolean {
    val classSymbol = session.run { type?.expandedSymbol } ?: return false
    val fqName = classSymbol.classId?.asSingleFqName() ?: return false

    val qualifiedName = if (isMutable) {
        if (fqName == StandardNames.FqNames.mutableList) return true
        StandardNames.FqNames.mutableList
    } else {
        if (fqName == StandardNames.FqNames.list) return true
        StandardNames.FqNames.list
    }
    return classSymbol.superTypes.reversed().any { superType ->
        val expandedSymbol = session.run { superType.expandedSymbol }
        val fqNameOfExpandedSymbol = expandedSymbol?.classId?.asSingleFqName()
        fqNameOfExpandedSymbol == qualifiedName
    }
}

private class ReplaceWithStdLibFix(private val methodName: String, private val receiver: String) : LocalQuickFix {
    override fun getName() = KotlinBundle.message("replace.with.std.lib.fix.text", receiver, methodName)

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val expression = descriptor.psiElement as? KtDotQualifiedExpression ?: return
        val callExpression = expression.callExpression ?: return
        val valueArguments = callExpression.valueArguments
        val firstArg = valueArguments.getOrNull(0)?.getArgumentExpression() ?: return
        val secondArg = valueArguments.getOrNull(1)?.getArgumentExpression()
        val factory = KtPsiFactory(project)
        val newExpression = if (secondArg != null) {
            if (methodName == "sort") {
                factory.createExpressionByPattern("$0.sortWith(Comparator $1)", firstArg, secondArg.text)
            } else {
                factory.createExpressionByPattern("$0.$methodName($1)", firstArg, secondArg)
            }
        } else {
            factory.createExpressionByPattern("$0.$methodName()", firstArg)
        }
        expression.replace(newExpression)
    }
}
