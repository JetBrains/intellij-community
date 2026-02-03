// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.packageDependencies

import com.intellij.packageDependencies.DependenciesBuilder
import com.intellij.packageDependencies.DependencyVisitorFactory
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtImportDirective
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

        override fun visitImportDirective(importDirective: KtImportDirective) {
            if (!myOptions.skipImports()) {
                visitElement(importDirective)
            }
        }
    }
}