// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.writeAccess
import org.jetbrains.kotlin.gradle.workspace.KotlinFacetSettingsChecker
import kotlin.reflect.KProperty1

private typealias FacetField = KProperty1<KotlinFacetSettings, *>

class KotlinFacetSettingsChecksConfiguration {
    var excludedFacetFields: Set<FacetField>? = null
    var includedFacetFields: Set<FacetField>? = null
}

private val TestConfigurationDslScope.config: KotlinFacetSettingsChecksConfiguration
    get() = writeAccess.getConfiguration(KotlinFacetSettingsChecker)

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
