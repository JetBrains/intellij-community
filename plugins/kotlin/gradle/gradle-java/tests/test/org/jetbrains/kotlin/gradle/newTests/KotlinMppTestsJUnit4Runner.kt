// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

import org.jetbrains.kotlin.gradle.newTests.testServices.KotlinTestPropertiesService
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod

class KotlinMppTestsJUnit4Runner(testClass: Class<*>) : BlockJUnit4ClassRunner(testClass) {
    override fun testName(method: FrameworkMethod?): String {
        val props = KotlinTestPropertiesService.constructFromEnvironment()
        val agpVersion = props.agpVersion
        val kgpVersion = props.kotlinGradlePluginVersion
        val gradleVersion = props.gradleVersion

        return super.testName(method) + "[$kgpVersion, $gradleVersion, AGP $agpVersion]"
    }
}
