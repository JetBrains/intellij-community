// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.SpecifyExplicitTypeQuickFix
import org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments

class HasPlatformTypeInspection(
    @JvmField var publicAPIOnly: Boolean = true,
    @JvmField var reportPlatformArguments: Boolean = false
) : AbstractKotlinInspection() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> {
        return object : KtVisitorVoid() {
            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                if (declaration !is KtCallableDeclaration || declaration.typeReference != null) return
                val nameIdentifier = declaration.nameIdentifier ?: return
                analyze(declaration) {
                    checkForPlatformType(declaration, nameIdentifier, holder)
                }
            }
        }
    }

    context(KaSession)
    private fun KaType.isFlexibleRecursive(): Boolean {
        if (hasFlexibleNullability) return true
        val classType = this as? KaClassType ?: return false
        return classType.typeArguments.any { it !is KaStarTypeProjection && it.type?.isFlexibleRecursive() == true }
    }

    private val publicApiVisibilities = setOf(
        KaSymbolVisibility.PUBLIC,
        KaSymbolVisibility.PROTECTED,
    )

    context(KaSession)
    private fun dangerousFlexibleTypeOrNull(
        declaration: KtCallableDeclaration, publicAPIOnly: Boolean, reportPlatformArguments: Boolean
    ): KaType? {
        when (declaration) {
            is KtFunction -> if (declaration.isLocal || declaration.hasDeclaredReturnType()) return null
            is KtProperty -> if (declaration.isLocal || declaration.typeReference != null) return null
            else -> return null
        }
        if (declaration.containingClassOrObject?.isLocal == true) return null
        if (publicAPIOnly && declaration.symbol.visibility !in publicApiVisibilities) return null
        val type = declaration.returnType
        if (type is KaDynamicType) return null
        if (reportPlatformArguments) {
            if (!type.isFlexibleRecursive()) return null
        } else {
            if (!type.hasFlexibleNullability) return null
        }

        return type
    }

    context(KaSession)
    fun checkForPlatformType(element: KtCallableDeclaration, nameIdentifier: PsiElement, holder: ProblemsHolder) {
        val dangerousFlexibleType = dangerousFlexibleTypeOrNull(element, publicAPIOnly, reportPlatformArguments) ?: return

        if (dangerousFlexibleType.canBeNull) {
            val nonNullableType = dangerousFlexibleType.withNullability(KaTypeNullability.NON_NULLABLE)
            val expression = element.node.findChildByType(KtTokens.EQ)?.psi?.getNextSiblingIgnoringWhitespaceAndComments()
            if (expression != null &&
                (!reportPlatformArguments || !nonNullableType.isFlexibleRecursive())
            ) {
                holder.registerProblem(
                    nameIdentifier,
                    KotlinBundle.message(
                        "declaration.has.type.inferred.from.a.platform.call.which.can.lead.to.unchecked.nullability.issues"
                    ),
                    IntentionWrapper(AddExclExclCallFix(expression)),
                    IntentionWrapper(
                        SpecifyExplicitTypeQuickFix(
                            element,
                            CallableReturnTypeUpdaterUtils.getTypeInfo(element)
                        )
                    )
                )
            }
        }
    }

    override fun getOptionsPane() = pane(
        checkbox("publicAPIOnly", KotlinBundle.message("apply.only.to.public.or.protected.members")),
        checkbox("reportPlatformArguments", KotlinBundle.message("report.for.types.with.platform.arguments"))
    )
}