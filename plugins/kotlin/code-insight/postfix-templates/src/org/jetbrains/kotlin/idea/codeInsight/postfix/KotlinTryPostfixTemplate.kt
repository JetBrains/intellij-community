// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal class KotlinTryPostfixTemplate : StringBasedPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ "try",
        /* example = */ "try { expr } catch (e: Exception) {}",
        /* selector = */ allExpressions(StatementFilter),
        /* provider = */ provider
    )

    override fun getTemplateString(element: PsiElement): String {
        val exceptionClasses = collectPossibleExceptions(element)

        return buildString {
            append("try {\n\$expr$\$END\$\n} ")

            for (exceptionClass in exceptionClasses) {
                append("catch (e: ${exceptionClass.asFqNameString()} ) {\nthrow e\n}")
            }
        }
    }

    private fun collectPossibleExceptions(element: PsiElement): List<ClassId> {
        val ktElement = element.getParentOfType<KtElement>(strict = false)
        if (ktElement != null) {
            val exceptionClasses = ExceptionClassCollector().also { ktElement.accept(it, null) }.exceptionClasses
            if (exceptionClasses.isNotEmpty()) {
                return exceptionClasses
            }
        }

        return listOf(ClassId.fromString("kotlin/Exception"))
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

@OptIn(KaAllowAnalysisOnEdt::class)
private class ExceptionClassCollector : KtTreeVisitor<Unit?>() {
    private companion object {
        val THROWS_ANNOTATION_FQ_NAMES = listOf(
            ClassId.fromString("kotlin/Throws"),
            ClassId.fromString("kotlin/jvm/Throws")
        )
    }

    private val mutableExceptionClasses = LinkedHashSet<ClassId>()
    private var hasLocalClasses = false

    val exceptionClasses: List<ClassId>
        get() = if (!hasLocalClasses) mutableExceptionClasses.toList() else emptyList()

    override fun visitCallExpression(expression: KtCallExpression, data: Unit?): Void? {
        processElement(expression)
        return super.visitCallExpression(expression, data)
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, data: Unit?): Void? {
        val shouldProcess = when (val parent = expression.parent) {
            is KtCallExpression -> expression != parent.calleeExpression
            is KtBinaryExpression -> expression != parent.operationReference
            is KtUnaryExpression -> expression != parent.operationReference
            else -> true
        }

        if (shouldProcess) {
            processElement(expression)
        }

        return super.visitSimpleNameExpression(expression, data)
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression, data: Unit?): Void? {
        processElement(expression)
        return super.visitBinaryExpression(expression, data)
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression, data: Unit?): Void? {
        processElement(expression)
        return super.visitUnaryExpression(expression, data)
    }

    private fun <T: KtElement> processElement(element: T) {
        if (hasLocalClasses) {
            return
        }

        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(element) {
                    processCall(element.resolveToCall())
                }
            }
        }
    }

    private fun processCall(callInfo: KaCallInfo?) {
        val call = (callInfo as? KaSuccessCallInfo)?.call ?: return

        when (call) {
            is KaSimpleFunctionCall -> processCallable(call.symbol)
            is KaSimpleVariableAccessCall -> {
                val symbol = call.symbol
                if (symbol is KaPropertySymbol) {
                    when (call.simpleAccess) {
                        is KaSimpleVariableAccess.Read -> symbol.getter?.let { processCallable(it) }
                        is KaSimpleVariableAccess.Write -> symbol.setter?.let { processCallable(it) }
                    }
                }
            }
            is KaCompoundVariableAccessCall -> processCallable(call.compoundOperation.operationPartiallyAppliedSymbol.symbol)
            is KaCompoundArrayAccessCall -> {
                processCallable(call.getPartiallyAppliedSymbol.symbol)
                processCallable(call.setPartiallyAppliedSymbol.symbol)
            }
            else -> {}
        }
    }

    private fun processCallable(symbol: KaCallableSymbol) {
        if (symbol.origin == KaSymbolOrigin.JAVA_SOURCE || symbol.origin == KaSymbolOrigin.JAVA_LIBRARY) {
            val javaMethod = symbol.psiSafe<PsiMethod>() ?: return
            for (type in javaMethod.throwsList.referencedTypes) {
                val classId = type.resolve()?.classIdIfNonLocal
                if (classId != null) {
                    mutableExceptionClasses.add(classId)
                } else {
                    hasLocalClasses = true
                }
            }

            return
        }

        for (classId in THROWS_ANNOTATION_FQ_NAMES) {
            for (annotation in symbol.annotations[classId]) {
                for (argument in annotation.arguments) {
                    processAnnotationValue(argument.expression)
                }
            }
        }
    }

    private fun processAnnotationValue(value: KaAnnotationValue) {
        when (value) {
          is KaAnnotationValue.ArrayValue -> value.values.forEach(::processAnnotationValue)
            is KaAnnotationValue.ClassLiteralValue -> {
                val type = value.type
                if (type is KaClassType) {
                    val classId = type.classId
                    if (classId.isLocal) {
                        hasLocalClasses = true
                    } else {
                        mutableExceptionClasses.add(classId)
                    }
                }
            }
            else -> {}
        }
    }
}