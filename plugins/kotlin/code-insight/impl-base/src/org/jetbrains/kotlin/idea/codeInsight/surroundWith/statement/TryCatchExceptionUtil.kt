// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.KaCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundArrayAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSuccessCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.isLocal
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitor
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType


object TryCatchExceptionUtil {
    @JvmStatic
    fun collectPossibleExceptions(element: PsiElement): List<ClassId> {
        val ktElement = element.getParentOfType<KtElement>(strict = false)
        if (ktElement != null) {
            val exceptionClasses = ExceptionClassCollector().also { ktElement.accept(it, null) }.exceptionClasses
            if (exceptionClasses.isNotEmpty()) {
                return exceptionClasses
            }
        }
        return listOf(ClassId.fromString("kotlin/Exception"))
    }
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
    var hasLocalClasses: Boolean = false
        private set

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

    private fun <T : KtElement> processElement(element: T) {
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
            is KaFunctionCall<*> -> processCallable(call.symbol)
            is KaVariableAccessCall -> {
                val symbol = call.symbol
                if (symbol is KaPropertySymbol) {
                    when (call.kind) {
                        is KaVariableAccessCall.Kind.Read -> symbol.getter?.let { processCallable(it) }
                        is KaVariableAccessCall.Kind.Write -> symbol.setter?.let { processCallable(it) }
                    }
                }
            }
            is KaCompoundVariableAccessCall -> processCallable(call.compoundOperation.operationCall.symbol)
            is KaCompoundArrayAccessCall -> {
                processCallable(call.getterCall.symbol)
                processCallable(call.setterCall.symbol)
            }
            else -> {}
        }
    }

    private fun processCallable(symbol: KaCallableSymbol) {
        val javaMethod = symbol.psiSafe<PsiMethod>()
        if (javaMethod != null) {
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

        THROWS_ANNOTATION_FQ_NAMES
            .flatMap { symbol.annotations[it] }
            .flatMap { it.arguments }
            .forEach { processAnnotationValue(it.expression) }

    }

    private fun processAnnotationValue(value: KaAnnotationValue) {
        when (value) {
            is KaAnnotationValue.ArrayValue -> value.values.forEach(::processAnnotationValue)
            is KaAnnotationValue.ClassLiteralValue -> {
                val type = value.type
                if (type is KaClassType) {
                    if (type.symbol.isLocal) {
                        hasLocalClasses = true
                    } else {
                        mutableExceptionClasses.add(type.classId)
                    }
                }
            }

            else -> {}
        }
    }
}