// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration.framework

import com.intellij.execution.junit.JUnitUtil
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFrameworkUtils.cached
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFrameworkUtils.isResolvable
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

open class JUnit3KotlinTestFramework : AbstractKotlinTestFramework() {

    override val markerClassFqn: String = JUnitUtil.TEST_CASE_CLASS
    override val disabledTestAnnotation: String = "org.junit.Ignore"

    override fun isTestClass(declaration: KtClassOrObject): Boolean {
        if (!super.isTestClass(declaration)) return false
        return cached(declaration) { isJUnit3TestClass(it) } ?: false
    }

    override fun isTestMethod(function: KtNamedFunction): Boolean {
        if (!super.isTestMethod(function)) return false
        return function.name?.startsWith("test") == true && isInTestClass(function)
    }

    private fun isInTestClass(function: KtNamedFunction): Boolean {
        val classOrObject = function.getParentOfType<KtClassOrObject>(true) ?: return false
        return isTestClass(classOrObject)
    }

    private fun isJUnit3TestClass(declaration: KtClassOrObject): Boolean {
        val superTypeListEntries = declaration.superTypeListEntries
            .filterIsInstance<KtSuperTypeCallEntry>().firstOrNull()
            ?: return false
        return superTypeListEntries.valueArgumentList?.arguments?.isEmpty() == true &&
                declaration.containingKtFile.isResolvable(
                    JUnitUtil.TEST_CASE_CLASS,
                    superTypeListEntries.calleeExpression.text
                )
    }
}