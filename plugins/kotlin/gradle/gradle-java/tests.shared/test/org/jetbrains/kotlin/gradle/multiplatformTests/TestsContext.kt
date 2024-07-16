// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.junit.runner.Description
import java.io.File

interface KotlinMppTestsContext {
    val gradleVersion: GradleVersion
    val kgpVersion: KotlinToolingVersion
    val agpVersion: String

    val description: Description

    /**
     * Root of the actual project, i.e. a copy in the temp-directory, where the test runs
     */
    val testProjectRoot: File

    val testProject: Project

    val codeInsightTestFixture: CodeInsightTestFixture

    /**
     * Root of the project in the testdata, i.e. file somewhere in `intellij`-repo
     */
    val testDataDirectory: File

    val gradleJdkPath: File

    val testConfiguration: TestConfiguration
}

class KotlinMppTestsContextImpl : KotlinMppTestsContext {
    override val testConfiguration: TestConfiguration = TestConfiguration()
    internal val testProperties: KotlinTestProperties = KotlinTestProperties.construct(testConfiguration)

    override lateinit var description: Description
    override lateinit var testProjectRoot: File
    override lateinit var testProject: Project
    override lateinit var gradleJdkPath: File

    internal var mutableCodeInsightTestFixture: CodeInsightTestFixture? = null
    override val codeInsightTestFixture: CodeInsightTestFixture get() = mutableCodeInsightTestFixture!!

    override val gradleVersion: GradleVersion
        get() = testProperties.gradleVersion

    override val kgpVersion: KotlinToolingVersion
        get() = testProperties.kotlinGradlePluginVersion

    override val agpVersion: String
        get() = testProperties.agpVersion

    override val testDataDirectory: File by lazy { computeTestDataDirectory(description) }
}
