// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration.framework

import com.intellij.codeInsight.TestFrameworks
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.testIntegration.TestFramework
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

interface KotlinPsiBasedTestFramework {

    fun responsibleFor(declaration: KtNamedDeclaration): Boolean

    fun checkTestClass(element: PsiElement?): ThreeState {
        if (element?.language != KotlinLanguage.INSTANCE) return ThreeState.NO
        val psiElement = (element as? KtLightElement<*, *>)?.kotlinOrigin ?: element
        val ktClassOrObject = psiElement.parentOfType<KtClassOrObject>(true) ?: return ThreeState.NO
        return checkTestClass(ktClassOrObject)
    }

    fun checkTestClass(declaration: KtClassOrObject): ThreeState

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

        private fun Language.isSubLanguage(language: Language): Boolean =
            language == Language.ANY || this.isKindOf(language)

        @JvmStatic
        fun findTestFramework(declaration: KtNamedDeclaration, psiOnlyChecks: Boolean = false): TestFramework? {
            val checkedFrameworksByName = HashMap<String, Language>()
            for (framework in TestFramework.EXTENSION_NAME.extensionList) {
                val frameworkName = framework.name
                val frameworkLanguage = framework.language

                if (!TestFrameworks.isSuitableByLanguage(declaration, framework)) continue

                val checkedFrameworkLanguage = checkedFrameworksByName[frameworkName]
                // if we've checked framework for more specific language - no reasons to check it again for more general language
                if (checkedFrameworkLanguage != null && checkedFrameworkLanguage.isSubLanguage(frameworkLanguage)) continue

                val kotlinPsiBasedTestFramework = framework as? KotlinPsiBasedTestFramework
                if (psiOnlyChecks && kotlinPsiBasedTestFramework == null) continue
                val checkTestClass =
                    kotlinPsiBasedTestFramework?.checkTestClass(declaration) ?: ThreeState.UNSURE

                val responsible = if (checkTestClass != ThreeState.UNSURE) {
                    checkedFrameworksByName[frameworkName] = frameworkLanguage
                    checkTestClass == ThreeState.YES && kotlinPsiBasedTestFramework!!.responsibleFor(declaration)
                } else when (declaration) {
                    is KtClassOrObject ->
                        declaration.toLightClass()?.let(framework::isTestClass) ?: false

                    is KtNamedFunction ->
                        declaration.toLightMethods().firstOrNull()?.let { framework.isTestMethod(it, false) }
                            ?: false

                    else -> false
                }
                if (responsible) return framework
            }
            return null
        }
    }
}