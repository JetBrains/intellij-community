// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.gradle.newTests.TestConfiguration
import org.jetbrains.kotlin.gradle.newTests.testFeatures.KotlinFacetSettingsChecksConfiguration
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.konan.isNative
import kotlin.reflect.KProperty1

private typealias FacetField = KProperty1<KotlinFacetSettings, *>

object KotlinFacetSettingsChecker : WorkspaceModelChecker<KotlinFacetSettingsChecksConfiguration>() {
    override fun createDefaultConfiguration(): KotlinFacetSettingsChecksConfiguration = KotlinFacetSettingsChecksConfiguration()

    override val classifier: String = "facets"

    override fun PrinterContext.process(module: Module) = with(printer) {
        val facetSettings = runReadAction {
            KotlinFacetSettingsProvider.getInstance(module.project)
                ?.getSettings(module)
        } ?: return

        val configuration = testConfiguration.getConfiguration(KotlinFacetSettingsChecker)

        val fieldsToPrint = configuration.computeFieldsToPrint()

        indented {
            for (field in fieldsToPrint) {
                val fieldValue = field.get(facetSettings)
                if (fieldValue == null || fieldValue is Collection<*> && fieldValue.isEmpty()) continue

                when (fieldValue) {
                    is TargetPlatform ->
                        println(field.name + " = " + fieldValue.componentPlatforms.joinToStringWithSorting(separator = "/"))

                    is LanguageVersion -> {
                        val valueSanitized = languageVersionSanitized(fieldValue, field, module)
                        println("${field.name} = $valueSanitized")
                    }

                    is Collection<*> ->
                        println(field.name + " = " + fieldValue.joinToStringWithSorting())

                    else -> println(field.name + " = " + fieldValue)
                }
            }
        }
    }

    override fun renderTestConfigurationDescription(testConfiguration: TestConfiguration): List<String> {
        val configuration = testConfiguration.getConfiguration(KotlinFacetSettingsChecker)
        return buildList {
            if (configuration.excludedFacetFields != null)
                add("excluding following facet fields: ${configuration.excludedFacetFields!!.joinToString { it.name }}")
            if (configuration.includedFacetFields != null) {
                add("showing only following facet fields: ${configuration.includedFacetFields!!.joinToString { it.name }}")
            }
        }
    }

    private fun PrinterContext.languageVersionSanitized(
        fieldValue: LanguageVersion,
        field: FacetField,
        module: Module
    ): String {
        // Currently, there's an issue for apiLevel or Native modules, see KT-56382, so substitution is disabled
        if (field == KotlinFacetSettings::apiLevel && module.platform.isNative()) return fieldValue.versionString

        val languageVersionOfKgp = LanguageVersion.fromFullVersionString(kotlinGradlePluginVersion.toString())
        return if (fieldValue == languageVersionOfKgp) CURRENT_KGP_LANGUAGE_VERSION_PLACEHOLDER else fieldValue.versionString
    }

    private fun KotlinFacetSettingsChecksConfiguration.computeFieldsToPrint(): Set<FacetField> {
        if (includedFacetFields != null) return includedFacetFields!!.ensureOnlyKnownFields()
        if (excludedFacetFields != null) return ALL_FACET_FIELDS_TO_PRINT - excludedFacetFields!!.ensureOnlyKnownFields()
        return ALL_FACET_FIELDS_TO_PRINT
    }

    private fun Set<FacetField>.ensureOnlyKnownFields(): Set<FacetField> {
        val diff = this - ALL_FACET_FIELDS_TO_PRINT
        require(diff.isEmpty()) {
            "Unknown KotlinFacetSettings fields requested: ${diff.joinToString { it.name }}\n" +
                    "Please, add them to `KotlinFaceSettingsPrinterContributor.ALL_FACET_FIELDS_TO_PRINT"
        }
        return this
    }

    private val ALL_FACET_FIELDS_TO_PRINT = setOf<FacetField>(
        KotlinFacetSettings::externalProjectId,
        KotlinFacetSettings::languageLevel,
        KotlinFacetSettings::apiLevel,
        KotlinFacetSettings::mppVersion,
        KotlinFacetSettings::dependsOnModuleNames,
        KotlinFacetSettings::additionalVisibleModuleNames,
        KotlinFacetSettings::targetPlatform
    )

    private val CURRENT_KGP_LANGUAGE_VERSION_PLACEHOLDER = "{{LATEST_STABLE}}"
}
