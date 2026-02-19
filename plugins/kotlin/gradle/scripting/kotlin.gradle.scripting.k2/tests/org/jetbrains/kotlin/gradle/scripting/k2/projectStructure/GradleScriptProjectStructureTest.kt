// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.projectStructure

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest.Companion.GRADLE_KOTLIN_FIXTURE
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.junit.jupiter.params.ParameterizedTest

@TestMetadata("testData/gradleScript")
internal class GradleScriptProjectStructureTest : AbstractGradleKotlinProjectStructureTest() {
    @ParameterizedTest
    @GradleTestSource("8.11")
    @TestMetadata("fromWizard.test")
    fun testSimple(gradleVersion: GradleVersion) {
        checkProjectStructure(gradleVersion, GRADLE_KOTLIN_FIXTURE)
    }
}

