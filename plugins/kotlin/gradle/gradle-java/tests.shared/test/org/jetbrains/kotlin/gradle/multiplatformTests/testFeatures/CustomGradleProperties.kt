// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures

import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess

const val enableKgpDependencyResolutionParam = "kotlin.mpp.import.enableKgpDependencyResolution"

object CustomGradlePropertiesTestFeature : TestFeature<CustomGradleProperties> {
    override fun createDefaultConfiguration(): CustomGradleProperties = CustomGradleProperties(mutableMapOf())

    override fun KotlinMppTestsContext.beforeTestExecution() {
        val propertiesToAdd = testConfiguration.getConfiguration(CustomGradlePropertiesTestFeature).testProperties
        if (propertiesToAdd.isEmpty()) return

        val gradleProperties = testProjectRoot.resolve("gradle.properties")
        val propertiesText = propertiesToAdd.entries.joinToString(prefix = "# Custom properties\n", postfix = "\n", separator = "\n") {
            (key, value) -> "$key=$value"
        }
        gradleProperties.appendText(propertiesText)
    }
}

class CustomGradleProperties(val testProperties: MutableMap<String, String>)

interface CustomGradlePropertiesDsl {
    fun TestConfigurationDslScope.addCustomGradleProperty(key: String, value: String) {
        writeAccess.getConfiguration(CustomGradlePropertiesTestFeature).testProperties[key] = value
    }
}
