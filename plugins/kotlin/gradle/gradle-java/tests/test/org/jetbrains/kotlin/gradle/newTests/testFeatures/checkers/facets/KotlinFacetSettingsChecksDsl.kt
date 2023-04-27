// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.facets

import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.writeAccess

interface KotlinFacetSettingsChecksDsl {
    fun TestConfigurationDslScope.onlyFacetFields(vararg fields: FacetField) {
        require(config.excludedFacetFields == null) { "onlyFields is mutually exclusive with exceptFields" }
        config.includedFacetFields = fields.toSet()
    }

    fun TestConfigurationDslScope.exceptFields(vararg fields: FacetField) {
        require(config.includedFacetFields == null) { "onlyFields is mutually exclusive with exceptFields" }
        config.excludedFacetFields = fields.toSet()
    }
}

private val TestConfigurationDslScope.config: KotlinFacetSettingsChecksConfiguration
    get() = writeAccess.getConfiguration(KotlinFacetSettingsChecker)
