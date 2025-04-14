// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.jdk2k

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.toArray
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.callExpressionVisitor

internal class ReplaceJavaStaticMethodWithKotlinAnalogInspection :
    KotlinApplicableInspectionBase<KtCallExpression, List<Replacement>>() {

    private fun findReplacementCandidatesByPsi(element: KtCallExpression): List<Replacement> {
        val callee = element.calleeExpression ?: return emptyList()

        return Holder.REPLACEMENTS[callee.text]?.filter {
            it.transformation.isApplicableByPsi(element)
        } ?: emptyList()
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        findReplacementCandidatesByPsi(element).isNotEmpty()

    override fun KaSession.prepareContext(element: KtCallExpression): List<Replacement>? {
        val replacements = findReplacementCandidatesByPsi(element).filter {
            it.isApplicable(element)
        }

        val javaMethodFqName = element.resolveToCall()
            ?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol?.callableId?.asSingleFqName()

        return replacements.filter {
            javaMethodFqName == FqName(it.javaMethodFqName) && it.transformation.isApplicableByAnalyze(element)
        }.takeIf { it.isNotEmpty() }
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtCallExpression,
        context: List<Replacement>,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor = createProblemDescriptor(
        /* psiElement = */ element,
        /* rangeInElement = */ rangeInElement,
        /* descriptionTemplate = */ KotlinBundle.message("should.be.replaced.with.kotlin.function"),
        /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */ onTheFly,
        /* ...fixes = */ *(context.map { it.asQuickFix() }.toArray(LocalQuickFix.EMPTY_ARRAY)),
    )

    private fun Replacement.asQuickFix() = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("replace.with.kotlin.analog.function.text", kotlinFunctionShortName) +
                    if (mayChangeSemantics) KotlinBundle.message("quickfix.text.suffix.may.change.semantics") else ""

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ): Unit = transformation(element, this@asQuickFix)
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }
}

private object Holder {
    private val JAVA_PRIMITIVES = listOf(
        "Integer" to "Int",
        "Long" to "Long",
        "Byte" to "Byte",
        "Character" to "Char",
        "Short" to "Short",
        "Double" to "Double",
        "Float" to "Float"
    ).flatMap { (javaPrimitive, kotlinPrimitive) ->
        listOf( // If radix is not a literal, it is considered an unsafe replacement (Kotlin has checks for an invalid radix)
            Replacement(
                "java.lang.$javaPrimitive.toString",
                "kotlin.text.toString",
                ToExtensionFunctionWithNonNullableReceiver,
                mayChangeSemantics = true
            ) {
                if (it.valueArguments.size != 2) return@Replacement false
                val radix = it.valueArguments.last().getArgumentExpression()
                radix !is KtConstantExpression
            }, // If radix is an int literal within bounds, the conversion is safe
            Replacement(
                "java.lang.$javaPrimitive.toString",
                "kotlin.text.toString",
                ToExtensionFunctionWithNonNullableReceiver
            ) {
                if (it.valueArguments.size != 2) return@Replacement false
                val radixExpr = it.valueArguments.last().getArgumentExpression()
                if (radixExpr !is KtConstantExpression) return@Replacement false
                val radix = radixExpr.text.toIntOrNull() ?: return@Replacement false
                radix in 2..36
            }, Replacement(
                "java.lang.$javaPrimitive.toString",
                "kotlin.primitives.$kotlinPrimitive.toString",
                ToExtensionFunctionWithNullableReceiver
            ) { call ->
                val valueArguments = call.valueArguments
                when {
                    valueArguments.size != 1 -> false
                    javaPrimitive != "Character" -> true
                    else -> {
                        val singleArgument = valueArguments.single().getArgumentExpression()
                        if (singleArgument != null) {
                            analyze(call) {
                              singleArgument.expressionType?.isCharType
                            } == true
                        } else {
                            false
                        }
                    }
                }
            }, Replacement(
                "java.lang.$javaPrimitive.compare",
                "kotlin.primitives.$kotlinPrimitive.compareTo",
                ToExtensionFunctionWithNonNullableReceiver
            )
        )
    }

    private val JAVA_IO = listOf(
        Replacement("java.io.PrintStream.print", "kotlin.io.print", transformation = ToKotlinPrint),
        Replacement("java.io.PrintStream.println", "kotlin.io.println", transformation = ToKotlinPrint)
    )

    // TODO: implement [java.lang.System.arraycopy]
    private val JAVA_SYSTEM = listOf(
        Replacement("java.lang.System.exit", "kotlin.system.exitProcess")
    )

    private val JAVA_MATH = listOf(
        Replacement("java.lang.Math.abs", "kotlin.math.abs"),
        Replacement("java.lang.Math.acos", "kotlin.math.acos"),
        Replacement("java.lang.Math.asin", "kotlin.math.asin"),
        Replacement("java.lang.Math.atan", "kotlin.math.atan"),
        Replacement("java.lang.Math.atan2", "kotlin.math.atan2"),
        Replacement(
            "java.lang.Math.cbrt",
            "kotlin.math.cbrt"
        ) { it.languageVersionSettings.apiVersion >= ApiVersion.KOTLIN_1_8 },
        Replacement("java.lang.Math.ceil", "kotlin.math.ceil"),
        Replacement("java.lang.Math.cos", "kotlin.math.cos"),
        Replacement("java.lang.Math.cosh", "kotlin.math.cosh"),
        Replacement("java.lang.Math.exp", "kotlin.math.exp"),
        Replacement("java.lang.Math.expm1", "kotlin.math.expm1"),
        Replacement("java.lang.Math.floor", "kotlin.math.floor"),
        Replacement("java.lang.Math.hypot", "kotlin.math.hypot"),
        Replacement("java.lang.Math.IEEEremainder", "kotlin.math.IEEErem", ToExtensionFunctionWithNonNullableReceiver),
        Replacement("java.lang.Math.log", "kotlin.math.ln"),
        Replacement("java.lang.Math.log1p", "kotlin.math.ln1p"),
        Replacement("java.lang.Math.log10", "kotlin.math.log10"),
        Replacement("java.lang.Math.max", "kotlin.math.max"),
        Replacement("java.lang.Math.max", "kotlin.ranges.coerceAtLeast", ToExtensionFunctionWithNonNullableReceiver),
        Replacement("java.lang.Math.min", "kotlin.math.min"),
        Replacement("java.lang.Math.min", "kotlin.ranges.coerceAtMost", ToExtensionFunctionWithNonNullableReceiver),
        Replacement("java.lang.Math.nextDown", "kotlin.math.nextDown", ToExtensionFunctionWithNonNullableReceiver),
        Replacement("java.lang.Math.nextAfter", "kotlin.math.nextTowards", ToExtensionFunctionWithNonNullableReceiver),
        Replacement("java.lang.Math.nextUp", "kotlin.math.nextUp", ToExtensionFunctionWithNonNullableReceiver),
        Replacement("java.lang.Math.pow", "kotlin.math.pow", ToExtensionFunctionWithNonNullableReceiver),
        Replacement("java.lang.Math.rint", "kotlin.math.round"),
        Replacement(
            "java.lang.Math.round",
            "kotlin.math.roundToLong",
            ToExtensionFunctionWithNonNullableReceiver,
            mayChangeSemantics = true
        ),
        Replacement(
            "java.lang.Math.round",
            "kotlin.math.roundToInt",
            ToExtensionFunctionWithNonNullableReceiver,
            mayChangeSemantics = true
        ),
        Replacement("java.lang.Math.signum", "kotlin.math.sign"),
        Replacement("java.lang.Math.sin", "kotlin.math.sin"),
        Replacement("java.lang.Math.sinh", "kotlin.math.sinh"),
        Replacement("java.lang.Math.sqrt", "kotlin.math.sqrt"),
        Replacement("java.lang.Math.tan", "kotlin.math.tan"),
        Replacement("java.lang.Math.tanh", "kotlin.math.tanh"),
        Replacement("java.lang.Math.copySign", "kotlin.math.withSign", ToExtensionFunctionWithNonNullableReceiver)
    )

    private val JAVA_COLLECTIONS = listOf(
        Replacement("java.util.Arrays.copyOf", "kotlin.collections.copyOf", ToExtensionFunctionWithNonNullableReceiver) {
            it.valueArguments.size == 2
        },
        Replacement(
            "java.util.Arrays.copyOfRange",
            "kotlin.collections.copyOfRange",
            ToExtensionFunctionWithNonNullableReceiver,
            mayChangeSemantics = true
        ),
        Replacement("java.util.Arrays.equals", "kotlin.collections.contentEquals", ToExtensionFunctionWithNullableReceiver) {
            it.valueArguments.size == 2
        },
        Replacement("java.util.Arrays.deepEquals", "kotlin.collections.contentDeepEquals", ToExtensionFunctionWithNullableReceiver),
        Replacement("java.util.Arrays.deepHashCode", "kotlin.collections.contentDeepHashCode", ToExtensionFunctionWithNullableReceiver),
        Replacement("java.util.Arrays.hashCode", "kotlin.collections.contentHashCode", ToExtensionFunctionWithNullableReceiver),
        Replacement("java.util.Arrays.deepToString", "kotlin.collections.contentDeepToString", ToExtensionFunctionWithNullableReceiver),
        Replacement("java.util.Arrays.toString", "kotlin.collections.contentToString", ToExtensionFunctionWithNullableReceiver),
        Replacement("java.util.Arrays.asList", "kotlin.collections.listOf"),
        Replacement("java.util.Arrays.asList", "kotlin.collections.mutableListOf"),

        Replacement("java.util.Set.of", "kotlin.collections.setOf"),
        Replacement("java.util.Set.of", "kotlin.collections.mutableSetOf"),
        Replacement("java.util.List.of", "kotlin.collections.listOf"),
        Replacement("java.util.List.of", "kotlin.collections.mutableListOf"),

        Replacement("java.util.Collections.binarySearch", "kotlin.collections.binarySearch", ToExtensionFunctionWithNonNullableReceiver),
        Replacement(
            "java.util.Collections.reverse",
            "kotlin.collections.reverse",
            ToExtensionFunctionWithNullableReceiverForMutableCollection
        ),
        Replacement("java.util.Collections.fill", "kotlin.collections.fill", ToExtensionFunctionWithNullableReceiverForMutableCollection),
        Replacement("java.util.Collections.sort", "kotlin.collections.sort", ToExtensionFunctionWithNullableReceiverForMutableCollection) {
            it.valueArguments.size == 1 || it.valueArguments.getOrNull(1)?.getArgumentExpression() is KtLambdaExpression
        },
        Replacement(
            "java.util.Collections.shuffle",
            "kotlin.collections.shuffle",
            ToExtensionFunctionWithNullableReceiverForMutableCollection
        ),
    )

    val REPLACEMENTS: Map<String, List<Replacement>> =
        (JAVA_MATH + JAVA_SYSTEM + JAVA_IO + JAVA_PRIMITIVES + JAVA_COLLECTIONS).groupBy { it.javaMethodShortName }
}

@FileModifier.SafeTypeForPreview
data class Replacement(
    val javaMethodFqName: String,
    val kotlinFunctionFqName: String,
    val transformation: Transformation = WithoutAdditionalTransformation,
    val mayChangeSemantics: Boolean = false,
    val isApplicable: (KtCallExpression) -> Boolean = { true }
) {
    private fun String.shortName() = takeLastWhile { it != '.' }

    val javaMethodShortName = javaMethodFqName.shortName()
    val kotlinFunctionShortName = kotlinFunctionFqName.shortName()

    val shouldAddImport = kotlinFunctionFqName.startsWith("kotlin.math")
}
