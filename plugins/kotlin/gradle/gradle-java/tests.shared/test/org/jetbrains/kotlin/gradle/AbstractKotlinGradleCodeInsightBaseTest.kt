// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightBaseTestCase
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatKotlinIsSupported

abstract class AbstractKotlinGradleCodeInsightBaseTest: GradleCodeInsightBaseTestCase() {

    override fun setUp() {
        assumeThatKotlinIsSupported(gradleVersion)
        super.setUp()
    }

    override fun tearDown() {
        runAll(
            { KotlinSdkType.removeKotlinSdkInTests() },
            { super.tearDown() }
        )
    }
}