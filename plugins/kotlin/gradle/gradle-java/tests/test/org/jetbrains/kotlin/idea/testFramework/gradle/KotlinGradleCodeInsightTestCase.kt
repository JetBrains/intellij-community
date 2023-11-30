// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testFramework.gradle

import com.intellij.testFramework.RunAll
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleProjectTestCase.Companion.KOTLIN_PROJECT
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatKotlinIsSupported

abstract class KotlinGradleCodeInsightTestCase : GradleCodeInsightTestCase() {

    override fun tearDown() {
        RunAll.runAll(
            { super.tearDown() },
            { KotlinSdkType.removeKotlinSdkInTests() }
        )
    }

    fun testKotlinProject(gradleVersion: GradleVersion, test: () -> Unit) {
        assumeThatKotlinIsSupported(gradleVersion)
        test(gradleVersion, KOTLIN_PROJECT, test)
    }
}