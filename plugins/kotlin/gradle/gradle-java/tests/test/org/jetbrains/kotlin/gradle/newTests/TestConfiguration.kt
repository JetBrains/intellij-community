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

    @Suppress("UNCHECKED_CAST")
    private fun <V : Any, K : TestFeature<V>> getOrPutConfiguration(feature: K, default: () -> V): V {
        return featuresConfiguration[feature] as V?
            ?: default().also { featuresConfiguration[feature] = it }
    }

    fun copy(): TestConfiguration = TestConfiguration(featuresConfiguration)
}
