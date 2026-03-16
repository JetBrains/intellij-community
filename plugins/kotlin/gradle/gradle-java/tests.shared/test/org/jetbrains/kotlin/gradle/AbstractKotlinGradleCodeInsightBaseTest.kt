// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.testFramework.common.runAll
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightBaseTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatKotlinIsSupported
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile

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

    protected fun testKotlinDslEmptyProject(gradleVersion: GradleVersion, test: () -> Unit) {
        test(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT, test)
    }

    companion object {
        private val KOTLIN_DSL_EMPTY_PROJECT = GradleTestFixtureBuilder.create("kotlin-dsl-empty-project") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("kotlin-dsl-empty-project")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN)
        }
    }
}