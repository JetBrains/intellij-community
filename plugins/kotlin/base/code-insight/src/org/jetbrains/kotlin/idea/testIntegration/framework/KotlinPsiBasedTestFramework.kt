// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration.framework

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

interface KotlinPsiBasedTestFramework {

    fun responsibleFor(declaration: KtNamedDeclaration): Boolean

    fun isTestClass(declaration: KtClassOrObject): Boolean

    fun isTestMethod(declaration: KtNamedFunction): Boolean

    fun isIgnoredMethod(declaration: KtNamedFunction): Boolean

    fun findSetUp(classOrObject: KtClassOrObject): KtNamedFunction? = null

    fun findTearDown(classOrObject: KtClassOrObject): KtNamedFunction? = null

    companion object {
        const val KOTLIN_TEST_TEST = "kotlin.test.Test"
        const val KOTLIN_TEST_IGNORE = "kotlin.test.Ignore"
        const val KOTLIN_TEST_BEFORE_TEST = "kotlin.test.BeforeTest"
        const val KOTLIN_TEST_AFTER_TEST = "kotlin.test.AfterTest"

        fun PsiElement?.asKtClassOrObject(): KtClassOrObject? =
            when (this) {
                is KtClassOrObject -> this
                is KtLightElement<*, *> -> this.kotlinOrigin as? KtClassOrObject
                else -> null
            }

        fun PsiElement?.asKtNamedFunction(): KtNamedFunction? =
            when (this) {
                is KtNamedFunction -> this
                is KtLightMethod -> kotlinOrigin as? KtNamedFunction
                else -> null
            }
    }
}