// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration.framework

import com.intellij.execution.junit.JUnitUtil
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework.Companion.KOTLIN_TEST_TEST
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFrameworkUtils.cached
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFrameworkUtils.getTopmostClass
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFrameworkUtils.isAnnotated
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class JUnit4KotlinTestFramework : AbstractKotlinTestFramework() {

    override val markerClassFqn: String = JUnitUtil.TEST_ANNOTATION
    override val disabledTestAnnotation: String = "org.junit.Ignore"

    override fun isTestClass(declaration: KtClassOrObject): Boolean {
        if (!super.isTestClass(declaration)) return false
        return cached(declaration) { isJUnit4TestClass(declaration) } ?: false
    }

    override fun isTestMethod(function: KtNamedFunction): Boolean {
        if (!super.isTestMethod(function)) return false
        if (function.annotationEntries.isEmpty()) return false
        return isJUnit4TestMethod(function)
    }

    private fun isJUnit4TestMethod(method: KtNamedFunction): Boolean {
        return method.isAnnotated(JUnitCommonClassNames.ORG_JUNIT_TEST) || method.isAnnotated(KOTLIN_TEST_TEST)
    }

    private fun isJUnit4TestClass(ktClassOrObject: KtClassOrObject): Boolean? {
        val topmostClass = getTopmostClass(ktClassOrObject)
        if (topmostClass == ktClassOrObject && ktClassOrObject.isAnnotated(JUnitUtil.RUN_WITH)) return true

        return ktClassOrObject.declarations.asSequence()
            .filterIsInstance<KtNamedFunction>()
            .any { isJUnit4TestMethod(it) }
    }
}
