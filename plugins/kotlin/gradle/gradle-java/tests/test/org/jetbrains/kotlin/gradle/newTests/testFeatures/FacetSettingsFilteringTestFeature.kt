// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.TestFeature
import org.jetbrains.kotlin.gradle.newTests.writeAccess
import kotlin.reflect.KProperty1

private typealias FacetField = KProperty1<KotlinFacetSettings, *>

object FacetSettingsFilteringTestFeature : TestFeature<FacetSettingsFilteringConfiguration> {
    override fun renderConfiguration(configuration: FacetSettingsFilteringConfiguration): List<String> {
        return buildList {
            if (configuration.excludedFacetFields != null)
                add("excluding following facet fields: ${configuration.excludedFacetFields!!.joinToString { it.name } }")
            if (configuration.includedFacetFields != null) {
                add("showing only following facet fields: ${configuration.includedFacetFields!!.joinToString { it.name } }")
            }
        }
    }

    override fun createDefaultConfiguration(): FacetSettingsFilteringConfiguration = FacetSettingsFilteringConfiguration()
}

class FacetSettingsFilteringConfiguration {
    var excludedFacetFields: Set<FacetField>? = null
    var includedFacetFields: Set<FacetField>? = null
}

private val TestConfigurationDslScope.config: FacetSettingsFilteringConfiguration
    get() = writeAccess.getConfiguration(FacetSettingsFilteringTestFeature)

interface FacetSettingsFilteringDsl {
    fun TestConfigurationDslScope.onlyFacetFields(vararg fields: FacetField) {
        require(config.excludedFacetFields == null) { "onlyFields is mutually exclusive with exceptFields" }
        config.includedFacetFields = fields.toSet()
    }

    fun TestConfigurationDslScope.exceptFields(vararg fields: FacetField) {
        require(config.includedFacetFields == null) { "onlyFields is mutually exclusive with exceptFields" }
        config.excludedFacetFields = fields.toSet()
    }
}
