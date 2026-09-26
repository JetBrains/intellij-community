// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration

import com.intellij.codeInsight.TestFrameworks
import com.intellij.testIntegration.TestFramework
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject

fun findSuitableFrameworks(klass: KtClassOrObject): List<TestFramework> {
    val lightClass = klass.toLightClass() ?: return emptyList()
    val testFrameworkNames = mutableSetOf<String>()
    val testFrameworks = buildList {
        for (testFramework in TestFramework.EXTENSION_NAME.extensionList) {
            if (TestFrameworks.isSuitableByLanguage(klass, testFramework) && testFramework.isTestClass(lightClass)) {
                val name = testFramework.name
                if (testFrameworkNames.add(name)) {
                    add(testFramework)
                }
            }
        }
        if (isEmpty()) {
            for (testFramework in TestFramework.EXTENSION_NAME.extensionList) {
                if (TestFrameworks.isSuitableByLanguage(klass, testFramework)  && testFramework.isPotentialTestClass(lightClass) ) {
                    val name = testFramework.name
                    if (testFrameworkNames.add(name)) {
                        add(testFramework)
                    }
                }
            }
        }
    }
    return testFrameworks
}
