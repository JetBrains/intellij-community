// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.codeInsight.TestFrameworks
import com.intellij.testIntegration.TestFramework
import com.intellij.util.SmartList
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject

fun findSuitableFrameworks(klass: KtClassOrObject): List<TestFramework> {
    val lightClass = klass.toLightClass() ?: return emptyList()
    val frameworks =
        TestFramework.EXTENSION_NAME.extensionList.filter {
            TestFrameworks.isSuitableByLanguage(klass, it)
        }
    return frameworks.firstOrNull { it.isTestClass(lightClass) }?.let { listOf(it) }
        ?: frameworks.filterTo(SmartList()) { it.isPotentialTestClass(lightClass) }
}
