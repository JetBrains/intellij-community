// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalQuickFix
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.isEnumValuesFunctionCall
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection.EnumValuesSoftDeprecateInspectionBase
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile


/**
 * Inspection checks that `entryValues<T>()` method can be replaced with `enumEntries<T>()`.
 *
 * See [KTIJ-35620](https://youtrack.jetbrains.com/issue/KTIJ-35620/Soft-deprecate-kotlin.enumValues-functions).
 */
internal class EnumValuesTopLevelFunctionSoftDeprecateInspection : EnumValuesSoftDeprecateInspectionBase() {

    override fun isEnumValuesSoftDeprecateEnabled(file: KtFile): Boolean {
        with(StandardKotlinNames.Enum.enumEntriesTopLevelFunction) {
            analyze(file) {
                return findTopLevelCallables(packageName, callableName).any()
            }
        }
    }

    override fun isDeprecatedExpression(callExpression: KtCallExpression): Boolean =
        callExpression.calleeExpression?.text == StandardKotlinNames.Enum.enumValues.shortName().asString()

    override fun getDisplayName(): String = KotlinBundle.message("inspection.enumValues.method.soft.deprecate.display.name")

    context(_: KaSession)
    override fun isSoftDeprecatedEnumValuesCall(resolvedCallSymbol: KaFunctionSymbol): Boolean =
        isEnumValuesFunctionCall(resolvedCallSymbol)

    context(_: KaSession)
    override fun isOptInRequired(callExpression: KtCallExpression, symbol: KaFunctionSymbol): Boolean = false

    context(_: KaSession)
    override fun isOptInAllowed(element: KtCallExpression, annotationClassId: ClassId): Boolean = true

    context(_: KaSession)
    override fun createQuickFix(callExpression: KtCallExpression, symbol: KaFunctionSymbol): LocalQuickFix? {
        if (symbol.callableId?.callableName != StandardKotlinNames.Enum.enumValues.shortName()) return null

        val resolvedCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val enumType = resolvedCall.typeArgumentsMapping.values.firstOrNull() ?: return null
        val enumClassSymbol = enumType.expandedSymbol ?: return null
        val enumClassQualifiedName = enumClassSymbol.classId?.asFqNameString() ?: return null
        return createQuickFix(getReplaceFixType(callExpression), enumClassQualifiedName)
    }

    override fun createQuickFix(fixType: ReplaceFixType, enumClassQualifiedName: String): LocalQuickFix {
        val fixExpression = "${StandardKotlinNames.Enum.enumEntries}<$enumClassQualifiedName>()"
        return ReplaceFix(fixType, "enumEntries<$enumClassQualifiedName>()", fixExpression)
    }

}

