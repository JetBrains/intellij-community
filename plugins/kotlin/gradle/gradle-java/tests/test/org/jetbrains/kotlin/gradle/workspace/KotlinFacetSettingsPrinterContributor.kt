// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.gradle.newTests.testFeatures.FacetSettingsFilteringConfiguration
import org.jetbrains.kotlin.gradle.newTests.testFeatures.FacetSettingsFilteringTestFeature
import org.jetbrains.kotlin.idea.projectModel.KotlinLanguageSettings
import org.jetbrains.kotlin.platform.TargetPlatform
import kotlin.reflect.KProperty1

private typealias FacetField = KProperty1<KotlinFacetSettings, *>

class KotlinFacetSettingsPrinterContributor : ModulePrinterContributor {
    override fun PrinterContext.process(module: Module) = with(printer) {
        val facetSettings = runReadAction {
            KotlinFacetSettingsProvider.getInstance(module.project)
                ?.getSettings(module)
        } ?: return

        val configuration = testConfiguration.getConfiguration(FacetSettingsFilteringTestFeature)

        val fieldsToPrint = configuration.computeFieldsToPrint()

        indented {
            for (field in fieldsToPrint) {
                val fieldValue = field.get(facetSettings)
                if (fieldValue == null || fieldValue is Collection<*> && fieldValue.isEmpty()) continue

                when (fieldValue) {
                    is TargetPlatform ->
                        println(field.name + " = " + fieldValue.componentPlatforms.joinToStringWithSorting(separator = "/"))

                    is LanguageVersion -> {
                        val valueSanitized = if (fieldValue == LanguageVersion.LATEST_STABLE)
                            CURRENT_LANGUAGE_VERSION_PLACEHOLDER
                        else
                            fieldValue.versionString
                        println("${field.name} = $valueSanitized")
                    }

                    is Collection<*> ->
                        println(field.name + " = " + fieldValue.joinToStringWithSorting() )

                    else -> println(field.name + " = " + fieldValue)
                }
            }
        }
    }

    private fun FacetSettingsFilteringConfiguration.computeFieldsToPrint(): Set<FacetField> {
        if (includedFacetFields != null) return includedFacetFields!!.ensureOnlyKnownFields()
        if (excludedFacetFields != null) return ALL_FACET_FIELDS_TO_PRINT - excludedFacetFields!!.ensureOnlyKnownFields()
        return ALL_FACET_FIELDS_TO_PRINT
    }

    private fun Set<FacetField>.ensureOnlyKnownFields(): Set<FacetField> {
        val diff = this - ALL_FACET_FIELDS_TO_PRINT
        require(diff.isEmpty()) {
            "Unknown KotlinFacetSettings fields requested: ${diff.joinToString { it.name } }\n" +
                    "Please, add them to `KotlinFaceSettingsPrinterContributor.ALL_FACET_FIELDS_TO_PRINT"
        }
        return this
    }

    companion object {
        private val ALL_FACET_FIELDS_TO_PRINT = setOf<FacetField>(
            KotlinFacetSettings::externalProjectId,
            KotlinFacetSettings::languageLevel,
            KotlinFacetSettings::apiLevel,
            KotlinFacetSettings::mppVersion,
            KotlinFacetSettings::dependsOnModuleNames,
            KotlinFacetSettings::additionalVisibleModuleNames,
            KotlinFacetSettings::targetPlatform
        )

        private val CURRENT_LANGUAGE_VERSION_PLACEHOLDER = "{{LATEST_STABLE}}"
    }
}
