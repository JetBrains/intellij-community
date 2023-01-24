// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.TestFeature
import org.jetbrains.kotlin.gradle.newTests.testProperties.AndroidGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.testProperties.GradleVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.testProperties.KotlinGradlePluginVersionTestsProperty
import org.jetbrains.kotlin.gradle.newTests.writeAccess
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import java.io.File

object DevModeTestFeature : TestFeature<DevModeTweaksImpl>  {
    override fun renderConfiguration(configuration: DevModeTweaksImpl): List<String> = emptyList()

    override fun createDefaultConfiguration(): DevModeTweaksImpl = DevModeTweaksImpl()
}

interface DevModeTweaks {
    /**
     * If non-null, the preprocessed test project (with all TestProperties substituted with actual values)
     * will be written to the destination '[path]/$testName'
     * If [overwriteExisting] is specified, '[path]/$testName' is *completely deleted* before writing
     */
    fun writeTestProjectTo(path: String, overwriteExisting: Boolean = false)

    /**
     * Can be used to override the versions used in tests
     *
     * NB: if you use dev { ... } block to configure this in test, test name won't be changed
     * (impossible to do because test name has to be determined before test started).
     * If you want overridden version to be displayed in the test name, overwrite default values
     * in [DevModeTweaksImpl]
     */
    var overrideGradleVersion: GradleVersionTestsProperty.Values?
    var overrideAgpVersion: AndroidGradlePluginVersionTestsProperty.Values?
    var overrideKgpVersion: KotlinGradlePluginVersionTestsProperty.Values?

    /**
     * Launches Gradle Daemon with suspend option, listening for debugger connection on [port]
     */
    fun enableGradleDebugWithSuspend(port: Int = 5005)
}

class DevModeTweaksImpl : DevModeTweaks {
    override var overrideGradleVersion: GradleVersionTestsProperty.Values? = null
        get() = field.checkNotOverriddenOnTeamcity()

    override var overrideAgpVersion: AndroidGradlePluginVersionTestsProperty.Values? = null
        get() = field.checkNotOverriddenOnTeamcity()

    override var overrideKgpVersion: KotlinGradlePluginVersionTestsProperty.Values? = null
        get() = field.checkNotOverriddenOnTeamcity()

    var writeTestProjectTo: File? = null
    var overwriteExistingProjectCopy: Boolean = false

    override fun writeTestProjectTo(path: String, overwriteExisting: Boolean) {
        writeTestProjectTo = File(path)
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
            error("Error: dev-block normally shouldn't be used on CI, as it can break tests stability/reliability\n" +
                          "Either remove it, or use dev(allowOnTeamcity = true) { ... } if you're absolutely sure what you're doing")
        }
        writeAccess.getConfiguration(DevModeTestFeature).config()
    }
}
