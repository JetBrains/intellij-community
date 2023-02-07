// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

import com.intellij.openapi.project.Project
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.junit.runner.Description
import java.io.File

interface KotlinMppTestsContext {
    val gradleVersion: GradleVersion
    val kgpVersion: KotlinToolingVersion
    val agpVersion: String

    val description: Description
    val testProjectRoot: File
    val testProject: Project
    val testDataDirectory: File

    val testConfiguration: TestConfiguration
}

class KotlinMppTestsContextImpl : KotlinMppTestsContext {
    internal val testProperties: KotlinTestProperties = KotlinTestProperties.constructFromEnvironment()

    override lateinit var description: Description
    override lateinit var testConfiguration: TestConfiguration
    override lateinit var testProjectRoot: File
    override lateinit var testProject: Project

    override val gradleVersion: GradleVersion
        get() = testProperties.gradleVersion

    override val kgpVersion: KotlinToolingVersion
        get() = testProperties.kotlinGradlePluginVersion

    override val agpVersion: String
        get() = testProperties.agpVersion

    override val testDataDirectory: File by lazy { computeTestDataDirectory(description) }
}
