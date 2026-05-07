// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets

import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess

interface KotlinFacetSettingsChecksDsl {
    fun TestConfigurationDslScope.onlyFacetFields(vararg fields: FacetField) {
        require(config.excludedFacetFields == null) { "onlyFields is mutually exclusive with exceptFields" }
        config.includedFacetFields = fields.toSet()
    }

    fun TestConfigurationDslScope.exceptFields(vararg fields: FacetField) {
        require(config.includedFacetFields == null) { "onlyFields is mutually exclusive with exceptFields" }
        config.excludedFacetFields = fields.toSet()
    }

    fun TestConfigurationDslScope.onlyCompilerArguments(vararg fields: CompilerArgumentField) {
        require(config.includedFacetFields?.contains(IKotlinFacetSettings::compilerArguments) == true) {
            "onlyCompilerArguments can only be used with compilerArguments facet field shown. " +
                    "E.g. `onlyFacetFields(IKotlinFacetSettings::compilerArguments)`"
        }
        config.includedCompilerArguments = fields.toSet()
    }
}

private val TestConfigurationDslScope.config: KotlinFacetSettingsChecksConfiguration
    get() = writeAccess.getConfiguration(KotlinFacetSettingsChecker)
