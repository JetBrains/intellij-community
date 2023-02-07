// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.workspace

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.gradle.newTests.AbstractTestChecker
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.contentRoots.ContentRootsChecksDsl
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.facets.KotlinFacetSettingsChecksDsl
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.orderEntries.OrderEntriesChecksDsl
import org.jetbrains.kotlin.gradle.newTests.writeAccess

interface WorkspaceChecksDsl : OrderEntriesChecksDsl, KotlinFacetSettingsChecksDsl, ContentRootsChecksDsl {
    fun TestConfigurationDslScope.onlyModules(@Language("RegExp") regex: String) {
        require(config.excludedModuleNames == null) { "'onlyModules' is mutually exclusive with 'excludeModules'" }
        config.includedModuleNames = regex.toRegex()
    }

    fun TestConfigurationDslScope.excludeModules(@Language("RegExp") regex: String) {
        require(config.includedModuleNames == null) { "'onlyModules' is mutually exclusive with 'excludeModules'" }
        config.excludedModuleNames = regex.toRegex()
    }

    fun TestConfigurationDslScope.onlyCheckers(vararg checkers: AbstractTestChecker<*>) {
        require(config.disableCheckers == null) { "'onlyCheckers' is mutually exclusive with 'disableCheckers'" }
        config.onlyCheckers = checkers.toSet()
    }

    fun TestConfigurationDslScope.disableCheckers(vararg checkers: AbstractTestChecker<*>) {
        require(config.onlyCheckers == null) { "'onlyCheckers' is mutually exclusive with 'disableCheckers'" }
        config.disableCheckers = checkers.toSet()
    }
}

private val TestConfigurationDslScope.config: GeneralWorkspaceChecksConfiguration
    get() = writeAccess.getConfiguration(GeneralWorkspaceChecks)
