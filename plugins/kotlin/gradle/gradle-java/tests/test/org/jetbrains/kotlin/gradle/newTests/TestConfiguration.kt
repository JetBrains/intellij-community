// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

/**
 * Use it as extension-receiver to extend DSL-scope in `doTest { <here> }`
 */
interface TestConfigurationDslScope

/**
 * Trick to hide low-level mutable methods (`getConfiguration`/`putConfiguration`) from clients.
 * Tests infrastructure will have to call `writeAccess`
 */
val TestConfigurationDslScope.writeAccess: TestConfiguration
    get() = this as TestConfiguration

class TestConfiguration(private val featuresConfiguration: MutableMap<TestFeature<*>, Any> = mutableMapOf()) : TestConfigurationDslScope {

    fun <V : Any, K : TestFeature<V>> getConfiguration(feature: K): V = getOrPutConfiguration(feature) { feature.createDefaultConfiguration() }

    private fun <V : Any, K : TestFeature<V>> getOrPutConfiguration(feature: K, default: () -> V): V {
        return featuresConfiguration[feature] as V?
            ?: default().also { featuresConfiguration[feature] = it }
    }

    fun renderHumanReadableFeaturesConfigurations(): String = buildString {
        for (feature in featuresConfiguration.keys.sortedBy { it::class.java.name }) {
            render(feature)
        }
    }

    private fun <V : Any, K : TestFeature<V>> StringBuilder.render(feature: K) {
        val configuration = getConfiguration(feature)
        with(feature) { renderConfiguration(configuration).forEach { appendLine("- $it") } }
    }

    fun copy(): TestConfiguration = TestConfiguration(featuresConfiguration)
}

interface TestFeature<V : Any> {
    /**
     * Renders human-readable description of test feature' configuration. It will be
     * rendered in the testdata
     *
     * General guidelines:
     * - return one or more strings. Each string will be rendered as a separate line, so it's
     *   a good idea go group related information together, and separate less related ones
     * - try to keep it short and informative, akin to commit message titles: 'hide stdlib', 'show order entries scopes'
     */
    fun renderConfiguration(configuration: V): List<String>

    fun createDefaultConfiguration(): V
}
