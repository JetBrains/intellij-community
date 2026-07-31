// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.libraries

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsights.impl.base.asQuickFix
import org.jetbrains.kotlin.idea.inspections.libraries.AddKotlinReflectQuickFixUtil
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor

private val FASTJSON2_PACKAGE: FqName = FqName("com.alibaba.fastjson2")
private const val KOTLIN_REFLECT_CLASS = "kotlin.reflect.jvm.ReflectJvmMapping"
private val CLASS_CLASS_ID: ClassId = ClassId.fromString("java/lang/Class")
private val TYPE_REFERENCE_CLASS_ID: ClassId = ClassId.fromString("com/alibaba/fastjson2/TypeReference")

@ApiStatus.Internal
class Fastjson2MissingKotlinReflectInspection :
    KotlinApplicableInspectionBase<KtCallExpression, Fastjson2MissingKotlinReflectInspection.Context>() {

    @JvmInline
    value class Context(
        val quickFix: LocalQuickFix,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor { expression ->
        visitTargetElement(expression, holder, isOnTheFly)
    }

    context(session: KaSession)
    override fun prepareContext(
        element: KtCallExpression,
    ): Context? {
        val symbol = element.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return null
        val callableId = symbol.callableId ?: return null

        // fastjson2 API is split between member functions (e.g. JSON.parseObject) and
        // top-level Kotlin extension functions (e.g. String.parseObject<T>()).
        // Member functions have a classId; top-level extensions do not.
        val isInFastjson2Package = callableId.classId?.packageFqName == FASTJSON2_PACKAGE
                || callableId.packageName == FASTJSON2_PACKAGE

        if (!isInFastjson2Package) return null

        // Calls without a target type (e.g. parseObject(text), parse(text)) deserialize
        // into JSONObject/JSONArray rather than a Kotlin class, so kotlin-reflect
        // is not needed and deserialization will work correctly without it.
        val hasTargetType = symbol.typeParameters.isNotEmpty() ||
                element.valueArguments.any { arg ->
                    val argType = arg.getArgumentExpression()
                        ?.expressionType
                        ?: return@any false
                    argType.classId == CLASS_CLASS_ID || argType.isSubtypeOf(TYPE_REFERENCE_CLASS_ID)
                }
        if (!hasTargetType) return null

        val module: Module = element.containingKtFile.module ?: return null
        if (hasKotlinReflect(module)) return null

        val quickFix = AddKotlinReflectQuickFixUtil
            .createQuickFix(element)
            ?.asQuickFix()
            ?: return null

        return Context(quickFix)
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtCallExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor {
        val range = element.calleeExpression?.textRangeInParent
        return createProblemDescriptor(
            element,
            range,
            KotlinBundle.message("inspection.fastjson2.missing.kotlin.reflect.problem"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            onTheFly,
            context.quickFix,
        )
    }
}

private fun hasKotlinReflect(module: Module): Boolean {
    val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
    return JavaPsiFacade.getInstance(module.project).findClass(KOTLIN_REFLECT_CLASS, scope) != null
}
