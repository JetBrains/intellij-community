// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.events.gradle

import com.intellij.testFramework.RunAll.Companion.runAll
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestNavigationTestCase

abstract class KotlinGradleTestNavigationTestCase : GradleTestNavigationTestCase() {

    override fun tearDown() {
        runAll(
            { super.tearDown() },
            { KotlinSdkType.removeKotlinSdkInTests() }
        )
    }
}