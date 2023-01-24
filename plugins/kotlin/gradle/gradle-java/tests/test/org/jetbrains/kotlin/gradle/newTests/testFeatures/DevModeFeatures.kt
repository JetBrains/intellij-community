// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.TestFeature
import org.jetbrains.kotlin.gradle.newTests.writeAccess
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
}

class DevModeTweaksImpl : DevModeTweaks {
    var writeTestProjectTo: File? = null
    var overwriteExisting: Boolean = false

    override fun writeTestProjectTo(path: String, overwriteExisting: Boolean) {
        writeTestProjectTo = File(path)
        this.overwriteExisting = overwriteExisting
    }
}

interface DevModeTweaksDsl {
    fun TestConfigurationDslScope.dev(config: DevModeTweaks.() -> Unit) {
        writeAccess.getConfiguration(DevModeTestFeature).config()
    }
}
