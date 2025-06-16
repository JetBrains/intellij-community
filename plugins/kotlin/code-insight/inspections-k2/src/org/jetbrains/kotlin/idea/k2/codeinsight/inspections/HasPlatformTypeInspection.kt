// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.modcommand.ModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.SpecifyExplicitTypeQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.asQuickFix
import org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class HasPlatformTypeInspection(
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

    private fun KaSession.isFlexibleRecursive(type: KaType): Boolean {
        if (type.hasFlexibleNullability) return true
        val classType = type as? KaClassType ?: return false
        return classType.typeArguments.any { arg -> arg !is KaStarTypeProjection && arg.type?.let { isFlexibleRecursive(it) } == true }
    }

    private val publicApiVisibilities = setOf(
        KaSymbolVisibility.PUBLIC,
        KaSymbolVisibility.PROTECTED,
    )

    private fun KaSession.dangerousFlexibleTypeOrNull(
        declaration: KtCallableDeclaration,
        publicAPIOnly: Boolean,
        reportPlatformArguments: Boolean
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
            if (!isFlexibleRecursive(type)) return null
        } else {
            if (!type.hasFlexibleNullability) return null
        }

        return type
    }

    private fun KaSession.checkForPlatformType(element: KtCallableDeclaration, nameIdentifier: PsiElement, holder: ProblemsHolder) {
        val dangerousFlexibleType = dangerousFlexibleTypeOrNull(element, publicAPIOnly, reportPlatformArguments) ?: return
        val fixes = mutableListOf<ModCommandAction>(
            SpecifyExplicitTypeQuickFix(element, CallableReturnTypeUpdaterUtils.getTypeInfo(element, useTemplate = holder.isOnTheFly))
        )

        if (dangerousFlexibleType.canBeNull) {
            val nonNullableType = dangerousFlexibleType.withNullability(KaTypeNullability.NON_NULLABLE)
            val expression = (element as? KtDeclarationWithInitializer)?.initializer

            // Only add this fix if it can actually fully resolve the problem
            if (expression != null && (!reportPlatformArguments || !isFlexibleRecursive(nonNullableType))) {
                fixes.add(AddExclExclCallFix(expression))
            }
        }

        holder.registerProblem(
            nameIdentifier,
            KotlinBundle.message("declaration.has.type.inferred.from.a.platform.call.which.can.lead.to.unchecked.nullability.issues"),
            *fixes.map { action -> action.asQuickFix() }.toTypedArray()
        )
    }

    override fun getOptionsPane() = pane(
        checkbox("publicAPIOnly", KotlinBundle.message("apply.only.to.public.or.protected.members")),
        checkbox("reportPlatformArguments", KotlinBundle.message("report.for.types.with.platform.arguments"))
    )
}