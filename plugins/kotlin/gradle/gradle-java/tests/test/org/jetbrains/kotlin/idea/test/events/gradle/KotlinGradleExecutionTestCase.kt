// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.events.gradle

import com.intellij.testFramework.RunAll.Companion.runAll
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleTestFixtureBuilderProvider
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.plugins.gradle.execution.test.events.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile

abstract class KotlinGradleExecutionTestCase : GradleExecutionTestCase() {

    override fun tearDown() {
        runAll(
          { super.tearDown() },
          { KotlinSdkType.removeKotlinSdkInTests() }
        )
    }

    fun testKotlinProject(gradleVersion: GradleVersion, test: () -> Unit) =
      test(gradleVersion, GradleTestFixtureBuilderProvider.KOTLIN_PROJECT, test)

    fun testKotlinJunit5Project(gradleVersion: GradleVersion, action: () -> Unit) {
        assertJunit5IsSupported(gradleVersion)
        testKotlinProject(gradleVersion, action)
    }

    fun testKotlinJunit4Project(gradleVersion: GradleVersion, action: () -> Unit) {
        test(gradleVersion, GradleTestFixtureBuilderProvider.KOTLIN_JUNIT4_FIXTURE, action)
    }

    fun testKotlinTestNGProject(gradleVersion: GradleVersion, action: () -> Unit) {
        test(gradleVersion, GradleTestFixtureBuilderProvider.KOTLIN_TESTNG_FIXTURE, action)
    }

    companion object {

    }
}