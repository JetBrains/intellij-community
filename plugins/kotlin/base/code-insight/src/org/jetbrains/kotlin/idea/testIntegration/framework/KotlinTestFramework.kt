// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration.framework

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * KotlinTestFramework is a partial replacement for [com.intellij.testIntegration.TestFramework]. The latter works with
 * [com.intellij.psi.PsiClass] and [com.intellij.psi.PsiMethod] interfaces whereas KotlinTestFramework support Kotlin analogues:
 * [KtClassOrObject] and [KtNamedFunction]. Although [com.intellij.psi.impl.light.LightClass] and [com.intellij.psi.impl.light.LightMethod]
 * are valid options for [com.intellij.testIntegration.TestFramework] usage, conversion takes time and results in poor performance. Kotlin
 * native PSI support provides a much better result.
 *
 * &nbsp;
 *
 * KotlinTestFramework is associated with a test class. Exact instance is got by means of [getApplicableFor] accepting either a class itself
 * or its test methods.
 *
 * &nbsp;
 *
 * If a framework is [responsibleFor] handling declarations of a test class then its [isTestClass] and [isTestMethod]
 * methods expect to get only this class's declarations.
 */
interface KotlinTestFramework {

    companion object {
        const val KOTLIN_TEST_TEST = "kotlin.test.Test"

        val EXTENSION_NAME: ExtensionPointName<KotlinTestFramework> =
            ExtensionPointName.create("org.jetbrains.kotlin.kotlinTestFramework")

        fun getApplicableFor(declaration: KtNamedDeclaration, slow: Boolean? ): KotlinTestFramework? =
            EXTENSION_NAME.extensionList.firstOrNull { (slow == null || it.isSlow == slow) && it.responsibleFor(declaration) }
    }

    /**
     * Indicates if provider could use some heavy calculations as resolve, or it is pure fast and no slow fallbacks.
     */
    val isSlow: Boolean


    /**
     * @param declaration is either a class or a method that is potentially a test one
     * @return true when this framework is aware of conventions/annotations used and can handle them
     */
    fun responsibleFor(declaration: KtNamedDeclaration): Boolean

    fun isTestClass(declaration: KtClassOrObject): Boolean

    fun isTestMethod(declaration: KtNamedFunction): Boolean

    fun isIgnoredMethod(declaration: KtNamedFunction): Boolean

    fun qualifiedName(declaration: KtNamedDeclaration): String?
}