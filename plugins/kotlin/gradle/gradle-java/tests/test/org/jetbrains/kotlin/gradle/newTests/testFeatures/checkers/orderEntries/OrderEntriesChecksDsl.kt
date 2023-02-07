// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.orderEntries

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.writeAccess

interface OrderEntriesChecksDsl {
    var TestConfigurationDslScope.hideStdlib: Boolean
        get() = config.hideStdlib
        set(value) { config.hideStdlib = value }

    var TestConfigurationDslScope.hideKotlinTest
        get() = config.hideKotlinTest
        set(value) { config.hideKotlinTest = value }

    var TestConfigurationDslScope.hideKotlinNativeDistribution
        get() = config.hideKonanDist
        set(value) { config.hideKonanDist = value }

    fun TestConfigurationDslScope.excludeDependencies(@Language("RegExp") regex: String) {
        require(config.onlyDependencies == null) { "excludeDependencies can not be used together with onlyDependencies" }
        config.excludeDependencies = regex.toRegex()
    }

    fun TestConfigurationDslScope.onlyDependencies(@Language("RegExp") regex: String) {
        require(config.excludeDependencies == null) { "onlyDependencies can not be used together with excludeDependencies" }
        config.onlyDependencies = regex.toRegex()
    }

    var TestConfigurationDslScope.sortDependencies
        get() = config.sortDependencies
        set(value) { config.sortDependencies = value }

}

private val TestConfigurationDslScope.config: OrderEntriesChecksConfiguration
    get() = writeAccess.getConfiguration(OrderEntriesChecker)
