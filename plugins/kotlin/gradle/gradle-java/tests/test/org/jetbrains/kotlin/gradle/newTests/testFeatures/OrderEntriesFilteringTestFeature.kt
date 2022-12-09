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

            if (configuration.excludeDependenciesRegex != null) {
                add("hiding dependencies matching ${configuration.excludeDependenciesRegex.toString()}")
            }
        }
    }

    override fun createDefaultConfiguration(): OrderEntriesFilteringConfiguration = OrderEntriesFilteringConfiguration()
}

class OrderEntriesFilteringConfiguration {
    var excludeDependenciesRegex: Regex? = null

    var hideStdlib: Boolean = false
    var hideKotlinTest: Boolean = false
    var hideKonanDist: Boolean = false

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

    fun TestConfigurationDslScope.excludeDependenciesRegex(@Language("Regex") regex: String) {
        config.excludeDependenciesRegex = regex.toRegex()
    }
}
