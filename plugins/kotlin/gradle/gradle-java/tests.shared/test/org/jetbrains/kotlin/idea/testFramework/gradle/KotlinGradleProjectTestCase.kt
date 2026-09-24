// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testFramework.gradle

import com.intellij.gradle.testFramework.GradleProjectTestCase
import com.intellij.gradle.testFramework.GradleTestFixtureBuilder
import com.intellij.gradle.testFramework.util.assertThatKotlinIsSupported
import com.intellij.gradle.testFramework.util.withBuildFile
import com.intellij.gradle.testFramework.util.withSettingsFile
import com.intellij.testFramework.RunAll
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl

abstract class KotlinGradleProjectTestCase : GradleProjectTestCase() {

    override fun tearDown() {
        RunAll.runAll(
            { super.tearDown() },
            { KotlinSdkType.removeKotlinSdkInTests() }
        )
    }

    fun testKotlinProject(gradleVersion: GradleVersion, test: () -> Unit) {
        assertThatKotlinIsSupported(gradleVersion)
        test(gradleVersion, KOTLIN_PROJECT, test)
    }

    companion object {

        val KOTLIN_PROJECT: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("kotlin-plugin-project") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("kotlin-plugin-project")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinJvmPlugin()
                withJUnit()
            }
            withDirectory("src/main/kotlin")
            withDirectory("src/test/kotlin")
        }
    }
}