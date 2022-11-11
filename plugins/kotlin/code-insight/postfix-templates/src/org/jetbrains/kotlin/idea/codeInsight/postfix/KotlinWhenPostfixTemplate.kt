// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtExpression

internal class KotlinWhenPostfixTemplate : StringBasedPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ "when",
        /* example = */ "when (expr) {}",
        /* selector = */ allExpressions(ValuedFilter, StatementFilter, ExpressionTypeFilter { isSealedType(it) }),
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

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun collectPossibleBranches(element: PsiElement): List<CaseBranch> {
        if (element !is KtExpression) {
            return emptyList()
        }

        allowAnalysisOnEdt {
            analyze(element) {
                val type = element.getKtType()
                if (type is KtNonErrorClassType) {
                    val klass = type.classSymbol
                    if (klass is KtNamedClassOrObjectSymbol) {
                        return when (klass.classKind) {
                            KtClassKind.ENUM_CLASS -> collectEnumBranches(klass)
                            else -> collectSealedClassInheritors(klass)
                        }
                    }
                }
            }
        }

        return emptyList()
    }

    private fun KtAnalysisSession.collectEnumBranches(klass: KtNamedClassOrObjectSymbol): List<CaseBranch> {
        val enumEntries = klass.getDeclaredMemberScope()
            .getCallableSymbols()
            .filterIsInstance<KtEnumEntrySymbol>()

        return buildList {
            for (enumEntry in enumEntries) {
                val callableId = enumEntry.callableIdIfNonLocal ?: return emptyList()
                add(CaseBranch.Callable(callableId))
            }
        }
    }

    private fun KtAnalysisSession.collectSealedClassInheritors(klass: KtNamedClassOrObjectSymbol): List<CaseBranch> {
        return mutableListOf<CaseBranch>().also { processSealedClassInheritor(klass, it) }
    }

    private fun KtAnalysisSession.processSealedClassInheritor(klass: KtNamedClassOrObjectSymbol, consumer: MutableList<CaseBranch>): Boolean {
        val classId = klass.classIdIfNonLocal ?: return false

        if (klass.classKind == KtClassKind.OBJECT) {
            consumer.add(CaseBranch.Object(classId))
            return true
        }

        if (klass.modality == Modality.SEALED) {
            val inheritors = klass.getSealedClassInheritors()
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

    override fun getElementToRemove(expr: PsiElement) = expr
}

private sealed class CaseBranch {
    class Callable(val callableId: CallableId) : CaseBranch()
    class Object(val classId: ClassId) : CaseBranch()
    class Instance(val classId: ClassId) : CaseBranch()
}

private fun isSealedType(type: KtType): Boolean {
    if (type is KtNonErrorClassType) {
        val symbol = type.classSymbol
        if (symbol is KtNamedClassOrObjectSymbol) {
            return symbol.classKind == KtClassKind.ENUM_CLASS || symbol.modality == Modality.SEALED
        }
    }

    return false
}