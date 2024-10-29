// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.execution.TestStateStorage
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClass

/**
 * Returns urls which can be used to query the [TestStateStorage].
 * This is a generic implementation; More specialized versions might exist.
 *
 * Will return
 * ```
 * package foo.bar
 *
 * class MyTest { // <- 'java:suite://foo.bar.MyTest'
 *     @Test
 *     fun doTest()   // <- 'java:suite://foo.bar.MyTest/doTest
 * }
 * ```
 */
@IntellijInternalApi
fun KtDeclaration.genericKotlinTestUrls(): List<String> {
    return when (this) {
        is KtClassOrObject -> listOf("java:suite://${this.fqName?.asString()}")
        is KtNamedFunction -> {
            val containingClass = this.containingClass()
            listOf("java:test://${containingClass?.fqName?.asString()}/${this.name}")
        }

        else -> emptyList()
    }
}
