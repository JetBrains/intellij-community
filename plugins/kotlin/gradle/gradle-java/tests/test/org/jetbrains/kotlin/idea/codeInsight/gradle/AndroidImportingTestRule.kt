// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase.AndroidImportingTest
import org.jetbrains.kotlin.tooling.core.withLinearClosure
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.junit.AssumptionViolatedException
import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

class AndroidImportingTestRule : MethodRule {

    private companion object {
        const val androidGradlePluginVersion = "7.4.0-beta03"
        val requiredGradleVersion: GradleVersion = GradleVersion.version("7.5")
    }

    var properties: Map<String, String> = emptyMap()
        private set

    override fun apply(base: Statement, method: FrameworkMethod, target: Any): Statement {
        /* Find AndroidImportingTest annotation on method */
        method.getAnnotation(AndroidImportingTest::class.java)
        /* Find AndroidImportingTest annotation in any parent */
            ?: method.declaringClass
                ?.withLinearClosure { it.declaringClass }.orEmpty()
                .firstNotNullOfOrNull { it.getAnnotation(AndroidImportingTest::class.java) }

            /* Return w/o annotation */
            ?: return base

        val gradleVersion = (target as GradleImportingTestCase).gradleVersion

        if (GradleVersion.version(gradleVersion) < requiredGradleVersion) {
            throw AssumptionViolatedException(
                "Android Test requires $requiredGradleVersion or higher. Found ${gradleVersion}"
            )
        } else {
            properties = mapOf(
                "android_gradle_plugin_version" to androidGradlePluginVersion,
                "compile_sdk_version" to "31",
                "build_tools_version" to "28.0.3",
            )
        }
        return base
    }
}
