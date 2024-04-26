// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.workspace

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractTestChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.contentRoots.ContentRootsChecksDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecksDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.orderEntries.OrderEntriesChecksDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess

interface WorkspaceChecksDsl : OrderEntriesChecksDsl, KotlinFacetSettingsChecksDsl, ContentRootsChecksDsl {
    fun TestConfigurationDslScope.onlyModules(@Language("RegExp") regex: String) {
        require(config.excludedModuleNames == null) { "'onlyModules' is mutually exclusive with 'excludeModules'" }
        config.includedModuleNames = regex.toRegex()
    }

    fun TestConfigurationDslScope.excludeModules(@Language("RegExp") regex: String) {
        require(config.includedModuleNames == null) { "'onlyModules' is mutually exclusive with 'excludeModules'" }
        config.excludedModuleNames = regex.toRegex()
    }

    fun TestConfigurationDslScope.onlyCheckers(vararg checkers: TestFeature<*>) {
        config.onlyCheckers = checkers.toSet()
    }

    fun TestConfigurationDslScope.disableCheckers(vararg checkers: TestFeature<*>) {
        config.disableCheckers = checkers.toSet()
    }

    fun TestConfigurationDslScope.onlyDependencies(@Language("RegExp") from: String, @Language("RegExp") to: String) {
        onlyModules(from)
        onlyDependencies(to)
    }

    var TestConfigurationDslScope.testClassifier: String?
        get() = config.testClassifier
        set(value) { config.testClassifier = value }
}

private val TestConfigurationDslScope.config: GeneralWorkspaceChecksConfiguration
    get() = writeAccess.getConfiguration(GeneralWorkspaceChecks)
