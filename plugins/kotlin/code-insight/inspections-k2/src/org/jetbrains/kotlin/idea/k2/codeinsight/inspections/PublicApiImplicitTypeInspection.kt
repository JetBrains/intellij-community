// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.SpecifyExplicitTypeQuickFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class PublicApiImplicitTypeInspection(
    @JvmField var reportInternal: Boolean = false,
    @JvmField var reportPrivate: Boolean = false
): AbstractKotlinInspection() {
    private val problemText: String
        get() {
            return if (!reportInternal && !reportPrivate)
                KotlinBundle.message("for.api.stability.it.s.recommended.to.specify.explicitly.public.protected.declaration.types")
            else
                KotlinBundle.message("for.api.stability.it.s.recommended.to.specify.explicitly.declaration.types")
        }


    @OptIn(KaExperimentalApi::class)
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> {
        return object : KtVisitorVoid() {
            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                if (declaration !is KtCallableDeclaration || declaration.typeReference != null) return
                val nameIdentifier = declaration.nameIdentifier ?: return

                if (declaration is KtFunction && (declaration.bodyBlockExpression != null || !declaration.hasBody())) return

                if (declaration.containingClassOrObject?.isLocal == true) return

                if (declaration is KtFunction && declaration.isLocal) return
                if (declaration is KtProperty && declaration.isLocal) return
                if (declaration is KtParameter) return

                analyze(declaration) {
                    if (reportPrivate || reportInternalOrPublic(declaration)) {
                        val fix = IntentionWrapper(SpecifyExplicitTypeQuickFix(declaration, CallableReturnTypeUpdaterUtils.getTypeInfo(declaration)))
                        holder.registerProblem(nameIdentifier, problemText, fix)
                    }
                }
            }

            context(KaSession)
            private fun reportInternalOrPublic(
                declaration: KtCallableDeclaration,
            ): Boolean  {
                val declarationSymbol = declaration.symbol
                return reportInternal && declarationSymbol.compilerVisibility.compareTo(Visibilities.Internal)?.let { it >= 0 } == true ||
                        declaration.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) == ExplicitApiMode.DISABLED && isPublicApi(declarationSymbol)
            }
        }
    }

    override fun getOptionsPane(): OptPane = pane(
        checkbox("reportInternal", KotlinBundle.message("apply.also.to.internal.members")),
        checkbox("reportPrivate", KotlinBundle.message("apply.also.to.private.members")))
}