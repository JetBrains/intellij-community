// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.sealedClassInheritors
import org.jetbrains.kotlin.analysis.api.components.staticDeclaredMemberScope
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtExpression

internal class KotlinWhenPostfixTemplate : StringBasedPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ "when",
        /* example = */ "when (expr) {}",
        /* selector = */ allExpressions(ValuedFilter, ExpressionTypeFilter { isSealedType(it) }),
        /* provider = */ provider
    )

    override fun getTemplateString(element: PsiElement): String {
        return buildString {
            val branches = collectPossibleBranches(element)

            append("when (\$expr\$) {")

            if (branches.isNotEmpty()) {
                for ((index, branch) in branches.withIndex()) {
                    when (branch) {
                        is CaseBranch.Callable -> {
                            append("\n")
                            append(branch.callableId.asSingleFqName().asString())
                        }
                        is CaseBranch.Object -> {
                            append("\n")
                            append(branch.classId.asFqNameString())
                        }
                        is CaseBranch.Instance -> {
                            append("\nis ")
                            append(branch.classId.asFqNameString())
                        }
                    }
                    append(" -> ")
                    if (index == 0) {
                        append("\$END\$")
                    }
                    append("TODO()")
                }
            } else {
                append("\nelse -> \$END\$TODO()")
            }

            append("\n}")
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun collectPossibleBranches(element: PsiElement): List<CaseBranch> {
        if (element !is KtExpression) {
            return emptyList()
        }

        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(element) {
                    val type = element.expressionType
                    if (type is KaClassType) {
                        val klass = type.symbol
                        if (klass is KaNamedClassSymbol) {
                            return when (klass.classKind) {
                                KaClassKind.ENUM_CLASS -> collectEnumBranches(klass)
                                else -> collectSealedClassInheritors(klass)
                            }
                        }
                    }
                }
            }
        }

        return emptyList()
    }

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun collectEnumBranches(klass: KaNamedClassSymbol): List<CaseBranch> {
        val enumEntries = klass.staticDeclaredMemberScope
            .callables
            .filterIsInstance<KaEnumEntrySymbol>()

        return buildList {
            for (enumEntry in enumEntries) {
                val callableId = enumEntry.callableId ?: return emptyList()
                add(CaseBranch.Callable(callableId))
            }
        }
    }

    context(_: KaSession)
    private fun collectSealedClassInheritors(klass: KaNamedClassSymbol): List<CaseBranch> {
        return mutableListOf<CaseBranch>().also { processSealedClassInheritor(klass, it) }
    }

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun processSealedClassInheritor(klass: KaNamedClassSymbol, consumer: MutableList<CaseBranch>): Boolean {
        val classId = klass.classId ?: return false

        if (klass.classKind == KaClassKind.OBJECT) {
            consumer.add(CaseBranch.Object(classId))
            return true
        }

        if (klass.modality == KaSymbolModality.SEALED) {
            val inheritors = klass.sealedClassInheritors
            if (inheritors.isNotEmpty()) {
                for (inheritor in inheritors) {
                    if (!processSealedClassInheritor(inheritor, consumer)) {
                        return false
                    }
                }
                return true
            }
        }

        consumer.add(CaseBranch.Instance(classId))
        return true
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

private sealed class CaseBranch {
    class Callable(val callableId: CallableId) : CaseBranch()
    class Object(val classId: ClassId) : CaseBranch()
    class Instance(val classId: ClassId) : CaseBranch()
}

private fun isSealedType(type: KaType): Boolean {
    if (type is KaClassType) {
        val symbol = type.symbol
        if (symbol is KaNamedClassSymbol) {
            return symbol.classKind == KaClassKind.ENUM_CLASS || symbol.modality == KaSymbolModality.SEALED
        }
    }

    return false
}