// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid


internal class LeakingThisInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                property.initializer?.let { findThisUsages(it, holder, isOnTheFly) }
            }

            override fun visitClassInitializer(initializer: KtClassInitializer) {
                findThisUsages(initializer, holder, isOnTheFly)
            }

            override fun visitConstructor(constructor: KtConstructor<*>) {
                findThisUsages(constructor, holder, isOnTheFly)
            }
        }

    private fun findThisUsages(
        initializer: KtElement,
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ) {
        initializer.accept(object : KtTreeVisitorVoid() {
            override fun visitThisExpression(expression: KtThisExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }

            override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {}
        })
    }

    private fun visitTargetElement(
        expression: KtThisExpression,
        holder: ProblemsHolder,
        @Suppress("UNUSED_PARAMETER") isOnTheFly: Boolean
    ) {
        isLeaking(expression) ?: return
        ApplicabilityRange.self(expression).forEach {
            holder.registerProblem(
                expression,
                it,
                KotlinBundle.message("inspection.leaking.this.display.message")
            )
        }
    }

    fun isLeaking(element: PsiElement): LeakingThisContext? {
        return when (element) {
            // `this.foo` / `this.method()` — the dot access itself is not a leak;
            // the result may still escape, but that is tracked via the outer expression.
            /*
            *           var c: A? = null
                        class A {
                            var b: Int = 42
                                set(value) { field = value; foo(); c = this}
                            init {
                                this.foo()
                                this.b = 10
                            }
                            fun foo() { c = this }
                        }*/
            is KtDotQualifiedExpression -> null
            // Transparent wrapper — check what the parenthesised expression is used for.
            is KtParenthesizedExpression -> isLeaking(element.parent)
            // `foo(this)`, `obj.bar(this)`, collection.add(this), etc.
            is KtValueArgument -> LeakingThisContext.LeakingThis
            // `field = this` (EQ) or `list += this` (PLUSEQ — appends to a collection).
            is KtBinaryExpression -> when (element.operationToken) {
                KtTokens.EQ, KtTokens.PLUSEQ -> LeakingThisContext.LeakingThis
                else -> null
            }
            // `this::method` — bound callable reference captures `this`.
            is KtCallableReferenceExpression -> LeakingThisContext.LeakingThis
            is KtThisExpression -> isLeaking(element.parent)
            else -> null
        }
    }

    sealed interface LeakingThisContext {
        object LeakingThis : LeakingThisContext
        //object CallLeakingThisFunction : LeakingThisContext
    }
}

