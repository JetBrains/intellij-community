// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly

internal class KotlinWrapIntoArrayPostfixTemplate : StringBasedPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ "arrayOf",
        /* example = */ "arrayOf(expr)",
        /* selector = */ allExpressions(ValuedFilter, NonPackageAndNonImportFilter),
        /* provider = */ provider
    )

    override fun getTemplateString(element: PsiElement): String {
        val functionName = getArrayFunctionName(element)
        return "$functionName(\$expr$)\$END$"
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

private val ARRAY_CLASS_ID = ClassId.fromString("kotlin/Array")

private val PRIMITIVES_TO_ARRAYS: Map<ClassId, String> = buildMap {
    fun register(primitiveFqName: FqName, arrayFqName: FqName) {
        val shortName = arrayFqName.shortName().asString()
        val functionName = when {
            shortName.startsWith("U") -> "u" + shortName.drop(1).decapitalizeAsciiOnly() + "Of"
            else -> shortName.decapitalizeAsciiOnly() + "Of"
        }
        put(ClassId.topLevel(primitiveFqName), "kotlin.$functionName")
    }

    for (primitiveType in PrimitiveType.values()) {
        register(primitiveType.typeFqName, primitiveType.arrayTypeFqName)
    }

    register(StandardNames.FqNames.uByteFqName, StandardNames.FqNames.uByteArrayFqName)
    register(StandardNames.FqNames.uShortFqName, StandardNames.FqNames.uShortArrayFqName)
    register(StandardNames.FqNames.uIntFqName, StandardNames.FqNames.uIntArrayFqName)
    register(StandardNames.FqNames.uLongFqName, StandardNames.FqNames.uLongArrayFqName)
}

@RequiresReadLock
@OptIn(KaAllowAnalysisOnEdt::class)
private fun getArrayFunctionName(element: PsiElement): String {
    if (element is KtExpression) {
        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(element) {
                    val expectedType = element.expectedType
                    if (expectedType != null && expectedType.isClassType(ARRAY_CLASS_ID)) {
                        return "kotlin.arrayOf"
                    }

                    val elementType = element.expressionType
                    if (elementType != null && elementType is KaClassType && !elementType.isMarkedNullable) {
                        val functionName = PRIMITIVES_TO_ARRAYS[elementType.classId]
                        if (functionName != null) {
                            return functionName
                        }
                    }
                }
            }
        }
    }

    return "kotlin.arrayOf"
}