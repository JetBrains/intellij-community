// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration.framework

import com.intellij.execution.junit.JUnitUtil
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFrameworkUtils.cached
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFrameworkUtils.isAnnotated
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFrameworkUtils.getTopmostClass
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework.Companion.KOTLIN_TEST_TEST
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

class JUnit5KotlinTestFramework : AbstractKotlinTestFramework() {

    override val markerClassFqn: String = JUnitUtil.TEST5_ANNOTATION
    override val disabledTestAnnotation: String = "org.junit.jupiter.api.Disabled"

    override fun isTestClass(declaration: KtClassOrObject): Boolean {
        if (!super.isTestClass(declaration)) return false
        return cached(declaration) { isJUnit5TestClass(declaration) } ?: return false
    }

    override fun isTestMethod(function: KtNamedFunction): Boolean {
        if (!super.isTestMethod(function)) return false
        if (function.annotationEntries.isEmpty()) return false
        return isJUnit5TestMethod(function)
    }

    private fun isJUnit5TestClass(ktClassOrObject: KtClassOrObject): Boolean {
        val topmostClass = getTopmostClass(ktClassOrObject)
        if (topmostClass == ktClassOrObject && ktClassOrObject.isAnnotated("org.junit.jupiter.api.extension.ExtendWith"))
            return true

        return ktClassOrObject.declarations.asSequence()
            .filterIsInstance<KtNamedFunction>()
            .any { isJUnit5TestMethod(it) }
    }

    private fun isJUnit5TestMethod(method: KtNamedFunction): Boolean {
        return with(method) {
            isAnnotated(JUnitUtil.TEST5_ANNOTATION)
                    || isAnnotated(KOTLIN_TEST_TEST)
                    || isAnnotated("org.junit.jupiter.params.ParameterizedTest")
                    || isAnnotated("org.junit.jupiter.api.RepeatedTest")
                    || isAnnotated("org.junit.jupiter.api.TestFactory")
                    || isAnnotated("org.junit.jupiter.api.TestTemplate")
        }
    }
}