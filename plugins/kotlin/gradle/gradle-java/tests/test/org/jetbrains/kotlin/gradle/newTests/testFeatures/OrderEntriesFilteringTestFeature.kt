// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.TestFeature
import org.jetbrains.kotlin.gradle.newTests.writeAccess

object OrderEntriesFilteringTestFeature : TestFeature<OrderEntriesFilteringConfiguration> {
    override fun renderConfiguration(configuration: OrderEntriesFilteringConfiguration): List<String> {
        val hiddenStandardDependencies = buildList<String> {
            if (configuration.hideStdlib) add("stdlib")
            if (configuration.hideKotlinTest) add("kotlin-test")
            if (configuration.hideKonanDist) add("Kotlin/Native distribution")
            if (configuration.hideSdkDependency) add("sdk")
            if (configuration.hideSelfDependency) add ("self")
        }

        return buildList {
            if (hiddenStandardDependencies.isNotEmpty())
                add("hiding following standard dependencies: ${hiddenStandardDependencies.joinToString()}")

            if (configuration.excludeDependencies != null) {
                add("hiding dependencies matching ${configuration.excludeDependencies.toString()}")
            }

            if (configuration.sortDependencies) {
                add("dependencies order is not checked")
            }
        }
    }

    override fun createDefaultConfiguration(): OrderEntriesFilteringConfiguration = OrderEntriesFilteringConfiguration()
}

class OrderEntriesFilteringConfiguration {
    var excludeDependencies: Regex? = null
    var onlyDependencies: Regex? = null

    var hideStdlib: Boolean = false
    var hideKotlinTest: Boolean = false
    var hideKonanDist: Boolean = false

    /**
     * Enables or disabled sorting of dependencies (based on the lexicographical order of their
     * string representation)
     *
     * This is technically incorrect, because dependencies order matters in general case. However,
     * the majority of test cases don't actually have such a configuration where any possible reordering
     * can cause issues and, actually, change said order quite frequently, leading to a lot of noisy
     * changes in testdata. Therefore, sorting is enabled by default
     */
    var sortDependencies: Boolean = true

    // Always hidden for now
    val hideSelfDependency: Boolean = true
    val hideSdkDependency: Boolean = true
}

private val TestConfigurationDslScope.config: OrderEntriesFilteringConfiguration
    get() = writeAccess.getConfiguration(OrderEntriesFilteringTestFeature)

interface OrderEntriesFilteringSupport {
    var TestConfigurationDslScope.hideStdlib: Boolean
        get() = config.hideStdlib
        set(value) { config.hideStdlib = value }

    var TestConfigurationDslScope.hideKotlinTest
        get() = config.hideKotlinTest
        set(value) { config.hideKotlinTest = value }

    var TestConfigurationDslScope.hideKotlinNativeDistribution
        get() = config.hideKonanDist
        set(value) { config.hideKonanDist = value }

    fun TestConfigurationDslScope.excludeDependencies(@Language("Regex") regex: String) {
        require(config.onlyDependencies == null) { "excludeDependencies can not be used together with onlyDependencies" }
        config.excludeDependencies = regex.toRegex()
    }

    fun TestConfigurationDslScope.onlyDependencies(@Language("Regex") regex: String) {
        require(config.excludeDependencies == null) { "onlyDependencies can not be used together with excludeDependencies" }
        config.onlyDependencies = regex.toRegex()
    }

    var TestConfigurationDslScope.sortDependencies
        get() = config.sortDependencies
        set(value) { config.sortDependencies = value }

}
