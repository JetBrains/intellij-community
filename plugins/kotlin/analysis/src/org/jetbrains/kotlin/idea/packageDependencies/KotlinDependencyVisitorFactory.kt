/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.packageDependencies

import com.intellij.packageDependencies.DependenciesBuilder
import com.intellij.packageDependencies.DependencyVisitorFactory
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiImportStatement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class KotlinDependencyVisitorFactory : DependencyVisitorFactory() {
    override fun getVisitor(processor: DependenciesBuilder.DependencyProcessor, options: VisitorOptions): PsiElementVisitor =
        KotlinDependencyVisitor(processor, options)

    inner class KotlinDependencyVisitor(
        private val myProcessor: DependenciesBuilder.DependencyProcessor,
        private val myOptions: VisitorOptions
    ) : KtTreeVisitorVoid() {
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
            super.visitReferenceExpression(expression)
            for (ref in expression.references) {
                val resolved = ref.resolve()
                val parent = expression.parent
                val place = when (val grandParent = parent.parent) {
                    is KtDotQualifiedExpression -> grandParent
                    else -> parent
                }
                if (resolved != null) myProcessor.process(place, resolved)
            }
        }

        fun visitImportStatement(statement: PsiImportStatement?) {
            if (!myOptions.skipImports()) {
                visitElement(statement!!)
            }
        }
    }
}