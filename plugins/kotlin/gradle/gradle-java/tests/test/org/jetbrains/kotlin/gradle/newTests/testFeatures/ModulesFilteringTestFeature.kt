// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.gradle.newTests.AbstractTestChecker
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.TestFeature
import org.jetbrains.kotlin.gradle.newTests.writeAccess

// Architectural oddity: used directly by [WorkspaceModelChecker], doesn't need to be installed explicitly
// because all specific checkers inherit [WorkspaceModelChecker]
object GeneralWorkspaceChecks : TestFeature<WorkspaceChecksGeneralConfiguration> {
    override fun createDefaultConfiguration(): WorkspaceChecksGeneralConfiguration = WorkspaceChecksGeneralConfiguration()
}

class WorkspaceChecksGeneralConfiguration {
    // includedModuleNames == null -> use default (include everything), but
    // includedModuleNames == emptySet() -> don't include anything (hide all modules)
    //
    // those two modes are mutually exclusive: if one isn't null, then other must be null (ensured in [FilterModulesSupport])
    var excludedModuleNames: Regex? = null
    var includedModuleNames: Regex? = null

    var hideTestModules: Boolean = false
    var hideProductionModules: Boolean = false

    var disableCheckers: Set<AbstractTestChecker<*>>? = null
    var onlyCheckers: Set<AbstractTestChecker<*>>? = null
}

private val TestConfigurationDslScope.config: WorkspaceChecksGeneralConfiguration
    get() = writeAccess.getConfiguration(GeneralWorkspaceChecks)

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
