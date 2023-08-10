// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.DevModeTweaks.Companion.WRITE_PROJECTS_TO_ENV_PROPERTY
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.AndroidGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.GradleVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.testProperties.KotlinGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import java.io.File

/**
 * Provides `dev`-block with assorted utilities for **local** development.
 *
 * Those tweaks are not supposed to be run on CI, usually. Use `dev(allowOnTeamcity = true) { ... }`
 * if you know what you're doing.
 */
interface DevModeTweaks {
    /**
     * Writes the preprocessed test project (with all TestProperties substituted with actual values)
     * to the destination 'actualPath/$testName', where 'actualPath' is computed as following:
     *   - actualPath = [toPath] if [toPath] is not null
     *   - actualPath = value of the env property [WRITE_PROJECTS_TO_ENV_PROPERTY] otherwise
     *
     * If [overwriteExisting] is specified, '[toPath]/$testName' is *completely deleted* before writing
     */
    fun dumpTestProject(toPath: String? = null, overwriteExisting: Boolean = false)

    /**
     * Can be used to override the versions used in tests
     *
     * NB: if you use dev { ... } block to configure this in test, test name won't be changed
     * (impossible to do because test name has to be determined before test started).
     *
     * If you want overridden version to be displayed in the test name, overwrite default values
     * in [DevModeTweaksImpl]
     * Note that overwriting default values **will fail CI-runs** because of irreconcilable incompatibilities
     * with how test runs are organized on the CI
     */
    var overrideGradleVersion: String?
    var overrideAgpVersion: String?
    var overrideKgpVersion: String?

    /**
     * Launches Gradle Daemon with suspend option, listening for debugger connection on [port]
     */
    fun enableGradleDebugWithSuspend(port: Int = 5005)

    companion object {
        const val WRITE_PROJECTS_TO_ENV_PROPERTY = "ORG_JETBRAINS_KOTLIN_MPP_TESTS_WRITE_PROJECTS_TO"
    }
}

object DevModeTestFeature : TestFeature<DevModeTweaksImpl> {
    override fun createDefaultConfiguration(): DevModeTweaksImpl = DevModeTweaksImpl()
}

class DevModeTweaksImpl : DevModeTweaks {
    /** See [GradleVersionTestsProperty] for some well-known values */
    override var overrideGradleVersion: String? = null
        get() = field.checkNotOverriddenOnTeamcity()

    /** See [AndroidGradlePluginVersionTestsProperty] for some well-known values */
    override var overrideAgpVersion: String? = null
        get() = field.checkNotOverriddenOnTeamcity()

    /** See [KotlinGradlePluginVersionTestsProperty] for some well-known values */
    override var overrideKgpVersion: String? = null
        get() = field.checkNotOverriddenOnTeamcity()

    var writeTestProjectTo: File? = null
    var overwriteExistingProjectCopy: Boolean = false

    override fun dumpTestProject(toPath: String?, overwriteExisting: Boolean) {
        val actualPath = toPath ?: System.getProperty(WRITE_PROJECTS_TO_ENV_PROPERTY)
        ?: error(
            "Can't find the path to write the test project.\n" +
                    "Either provide the path explicitly in writeTestProjectTo, or set the env property $WRITE_PROJECTS_TO_ENV_PROPERTY"
        )
        writeTestProjectTo = File(actualPath)
        this.overwriteExistingProjectCopy = overwriteExisting
    }

    override fun enableGradleDebugWithSuspend(port: Int) {
        GradleSystemSettings.getInstance().gradleVmOptions = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=$port"
    }

    private fun <T> T.checkNotOverriddenOnTeamcity(): T {
        if (this != null && UsefulTestCase.IS_UNDER_TEAMCITY) {
            error(
                """
                Error: overriding tests-dependencies versions globally is not allowed on CI
                Most likely, you've changed the default values in properties of DevModeTweaksImpl
                """.trimIndent()
            )
        }
        return this
    }
}

interface DevModeTweaksDsl {
    fun TestConfigurationDslScope.dev(allowOnTeamcity: Boolean = false, config: DevModeTweaks.() -> Unit) {
        if (UsefulTestCase.IS_UNDER_TEAMCITY && !allowOnTeamcity) {
            error(
                "Error: dev-block normally shouldn't be used on CI, as it can break tests stability/reliability\n" +
                        "Either remove it, or use dev(allowOnTeamcity = true) { ... } if you're absolutely sure what you're doing"
            )
        }
        writeAccess.getConfiguration(DevModeTestFeature).config()
    }
}
