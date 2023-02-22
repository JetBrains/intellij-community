// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

class KotlinGradleModelTest: MultiplePluginVersionGradleImportingTestCase() {
    @Test
    @PluginTargetVersions(pluginVersion = "1.5+")
    fun testKotlinGradleModel() {
        configureByFiles()

        val builtKotlinGradleModel = buildGradleModel<KotlinGradleModel>()

        run {
            val model = builtKotlinGradleModel.getNotNullByProjectPathOrThrow(":")
            kotlin.test.assertNotNull(model.cachedCompilerArgumentsBySourceSet["main"])

            // In current versions of KGP, the classpath is always included in arguments
            // The check here should become `isEmpty` instead when the test starts running
            // with new version of KGP that supports omitting the classpath
            kotlin.test.assertTrue(model.cachedCompilerArgumentsBySourceSet["main"]!!
                                       .currentCompilerArguments.classpathParts.data.isNotEmpty())
        }
    }

}