// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.liveTemplates.k2.macro

import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.liveTemplates.macro.AbstractAnonymousSuperMacro
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

internal class SymbolBasedAnonymousSuperMacro : AbstractAnonymousSuperMacro() {
    @OptIn(KtAllowAnalysisOnEdt::class, KtAllowAnalysisFromWriteAction::class)
    override fun resolveSupertypes(expression: KtExpression, file: KtFile): Collection<PsiNamedElement> {
        allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                analyze(expression) {
                    val scope = file.getScopeContextForPosition(expression).getCompositeScope()
                    return scope.getClassifierSymbols()
                        .filterIsInstance<KtNamedClassOrObjectSymbol>()
                        .filter { shouldSuggest(it) }
                        .filter { symbol ->
                            when (symbol.classKind) {
                                KtClassKind.CLASS -> symbol.modality in listOf(Modality.OPEN, Modality.ABSTRACT)
                                KtClassKind.INTERFACE -> true
                                KtClassKind.ANNOTATION_CLASS -> symbol.origin != KtSymbolOrigin.JAVA
                                else -> false
                            }
                        }
                        .mapNotNull { it.psi as? PsiNamedElement }
                        .toList()
                }
            }
        }
    }

    private fun shouldSuggest(declaration: KtNamedClassOrObjectSymbol): Boolean {
        val classId = declaration.classIdIfNonLocal
        if (classId != null) {
            val packageName = classId.packageFqName.asString()
            for (forbiddenPackageName in FORBIDDEN_PACKAGE_NAMES) {
                if (packageName == forbiddenPackageName || packageName.startsWith("$forbiddenPackageName.")) {
                    return false
                }
            }
        }

        return true
    }
}

private val FORBIDDEN_PACKAGE_NAMES = listOf("kotlin", "java.lang")
