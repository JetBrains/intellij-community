// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testFramework.gradle

import com.intellij.testFramework.RunAll
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.plugins.gradle.testFramework.GradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatKotlinIsSupported
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile

abstract class KotlinGradleProjectTestCase : GradleProjectTestCase() {

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

    companion object {

        val KOTLIN_PROJECT: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("kotlin-plugin-project") { gradleVersion ->
            withSettingsFile(gradleVersion, useKotlinDsl = true) {
                setProjectName("kotlin-plugin-project")
            }
            withBuildFile(gradleVersion, useKotlinDsl = true) {
                withKotlinJvmPlugin()
                withJUnit()
            }
            withDirectory("src/main/kotlin")
            withDirectory("src/test/kotlin")
        }
    }
}