// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.DeletePsiElementsFix
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.GenerateEqualsFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.GenerateHashCodeFix
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.classOrObjectVisitor

internal class EqualsOrHashCodeInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return classOrObjectVisitor(fun(classOrObject) {
            val nameIdentifier = classOrObject.nameIdentifier ?: return
            if (classOrObject.declarations.none { it.name == "equals" || it.name == "hashCode" }) return
            val (equalsDeclaration, hashCodeDeclaration) = analyze(classOrObject) {
                val classOrObjectMemberDeclarations = classOrObject.declarations
                Pair(
                    classOrObjectMemberDeclarations.singleOrNull {
                        val function = it.symbol as? KaNamedFunctionSymbol ?: return@singleOrNull false
                        GenerateEqualsAndHashCodeUtils.matchesEqualsMethodSignature(function)
                    } as? KtNamedFunction,
                    classOrObjectMemberDeclarations.singleOrNull {
                        val function = it.symbol as? KaNamedFunctionSymbol ?: return@singleOrNull false
                        GenerateEqualsAndHashCodeUtils.matchesHashCodeMethodSignature(function)
                    } as? KtNamedFunction,
                )
            }
            if (equalsDeclaration == null && hashCodeDeclaration == null) return

            when (classOrObject) {
                is KtObjectDeclaration -> {
                    if (classOrObject.superTypeListEntries.isNotEmpty()) return
                    holder.registerProblem(
                        nameIdentifier,
                        KotlinBundle.message("equals.hashcode.in.object.declaration"),
                        DeletePsiElementsFix(
                            listOfNotNull(equalsDeclaration?.createSmartPointer(), hashCodeDeclaration?.createSmartPointer())
                        )
                    )
                }

                is KtClass -> {
                    if (equalsDeclaration != null && hashCodeDeclaration != null) return
                    val description = InspectionsBundle.message(
                        "inspection.equals.hashcode.only.one.defined.problem.descriptor",
                        if (equalsDeclaration != null) "<code>equals()</code>" else "<code>hashCode()</code>",
                        if (equalsDeclaration != null) "<code>hashCode()</code>" else "<code>equals()</code>"
                    )

                    val fix = if (equalsDeclaration != null) {
                        GenerateHashCodeFix(GenerateEqualsAndHashCodeUtils.generateHashCode(classOrObject))
                    } else {
                        GenerateEqualsFix(GenerateEqualsAndHashCodeUtils.generateEquals(classOrObject))
                    }

                    holder.registerProblem(
                        nameIdentifier,
                        description,
                        fix,
                    )
                }

                else -> return
            }
        })
    }
}