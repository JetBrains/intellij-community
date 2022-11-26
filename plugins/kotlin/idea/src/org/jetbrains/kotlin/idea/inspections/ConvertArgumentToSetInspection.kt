// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.ConvertArgumentToSetIntention.Companion.getConvertibleArguments
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*

/**
 * Detects function calls where an argument could be converted to `Set` to improve performance.
 * The quick-fix appends `toSet()` conversion to the argument.
 *
 * Functions like `Iterable.minus` or `Iterable.subtract` invoke the `contains` method of their argument.
 * If the argument is not a `Set` (especially if it is an `Array`, `ArrayList`, or `Sequence`),
 * its conversion to `Set` may improve performance.
 *
 * Note: this intention is a part of preparations to Kotlin stdlib changes:
 * https://youtrack.jetbrains.com/issue/KTIJ-19104
 *
 * @see `org.jetbrains.kotlin.idea.intentions.ConvertArgumentToSetIntention` for detailed description
 * and implementation details.
 */

class ConvertArgumentToSetInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return expressionVisitor { expression ->
            getConvertibleArguments(expression).forEach {
                holder.registerProblem(
                    it,
                    KotlinBundle.message("can.convert.argument.to.set"),
                    ConvertArgumentToSetFix()
                )
            }
        }
    }
}

private class ConvertArgumentToSetFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("convert.argument.to.set.fix.text")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        element.replace(KtPsiFactory(project).createExpressionByPattern("$0.toSet()", element))
    }
}
