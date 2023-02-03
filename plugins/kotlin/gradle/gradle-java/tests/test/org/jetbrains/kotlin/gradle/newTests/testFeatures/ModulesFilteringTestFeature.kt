// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.TestFeature
import org.jetbrains.kotlin.gradle.newTests.writeAccess

object FilterModulesTestFeature : TestFeature<ModulesFilteringConfiguration> {
    override fun renderConfiguration(configuration: ModulesFilteringConfiguration): List<String> = buildList {
        if (configuration.excludedModuleNames != null) add("hiding source modules matching ${configuration.excludedModuleNames!!}")
        if (configuration.includedModuleNames != null) add("showing only source modules matching ${configuration.includedModuleNames!!}")

        if (configuration.hideTestModules) add("hiding test source modules")
        if (configuration.hideProductionModules) add("hiding production source modules")
    }

    override fun createDefaultConfiguration(): ModulesFilteringConfiguration = ModulesFilteringConfiguration()
}

class ModulesFilteringConfiguration {
    // includedModuleNames == null -> use default (include everything), but
    // includedModuleNames == emptySet() -> don't include anything (hide all modules)
    //
    // those two modes are mutually exclusive: if one isn't null, then other must be null (ensured in [FilterModulesSupport])
    var excludedModuleNames: Regex? = null
    var includedModuleNames: Regex? = null

    var hideTestModules: Boolean = false
    var hideProductionModules: Boolean = false
}

private val TestConfigurationDslScope.config: ModulesFilteringConfiguration
    get() = writeAccess.getConfiguration(FilterModulesTestFeature)

interface ModulesFilteringDsl {
    fun TestConfigurationDslScope.onlyModules(@Language("RegExp") regex: String) {
        val config = config
        require(config.excludedModuleNames == null) { "'onlyModules' is mutually exclusive with 'excludeModules'" }
        config.includedModuleNames = regex.toRegex()
    }

    fun TestConfigurationDslScope.excludeModules(@Language("RegExp") regex: String) {
        val config = config
        require(config.includedModuleNames == null) { "'onlyModules' is mutually exclusive with 'excludeModules'" }
        config.excludedModuleNames = regex.toRegex()
    }
}
