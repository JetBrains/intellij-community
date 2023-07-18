// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfiguration
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.*
import org.jetbrains.kotlin.platform.TargetPlatform
import kotlin.reflect.KProperty1

internal typealias FacetField = KProperty1<KotlinFacetSettings, *>

object KotlinFacetSettingsChecker : WorkspaceModelChecker<KotlinFacetSettingsChecksConfiguration>(respectOrder = true) {
    override fun createDefaultConfiguration(): KotlinFacetSettingsChecksConfiguration = KotlinFacetSettingsChecksConfiguration()

    override val classifier: String = "facets"

    override fun PrinterContext.buildReportDataForModule(module: Module): List<ModuleReportData> {
        val facetSettings = runReadAction {
            KotlinFacetSettingsProvider.getInstance(module.project)
                ?.getSettings(module)
        } ?: return emptyList()

        val configuration = testConfiguration.getConfiguration(KotlinFacetSettingsChecker)

        val fieldsToPrint = configuration.computeFieldsToPrint()

        return fieldsToPrint.mapNotNull {
            renderFacetField(it, it.get(facetSettings), module)?.let { ModuleReportData(it) }
        }
    }

    private fun PrinterContext.renderFacetField(field: FacetField, fieldValue: Any?, module: Module): String? {
        if (fieldValue == null || fieldValue is Collection<*> && fieldValue.isEmpty()) return null

        return when (fieldValue) {
            is TargetPlatform ->
                field.name + " = " + fieldValue.componentPlatforms.joinToStringWithSorting(separator = "/")

            is LanguageVersion -> {
                val valueSanitized = languageVersionSanitized(fieldValue)
                "${field.name} = $valueSanitized"
            }

            is Collection<*> -> field.name + " = " + fieldValue.joinToStringWithSorting()

            is CompilerSettings ->
                fieldValue.additionalArguments.filterOutInternalArguments().let {
                    if (it.isNotEmpty()) field.name + " = " + it else null
                }

            else -> field.name + " = " + fieldValue
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

    private fun PrinterContext.languageVersionSanitized(fieldValue: LanguageVersion): String {
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
        KotlinFacetSettings::targetPlatform,
        KotlinFacetSettings::compilerSettings
    )

    private val CURRENT_KGP_LANGUAGE_VERSION_PLACEHOLDER = "{{LATEST_STABLE}}"

    // Possible input: "-Xparam1=value1 -Xflag -param2 value2"
    private fun String.filterOutInternalArguments() =
        if (isEmpty()) this
        else substring(1) // drop "-" for the first element
            .split(" -")
            .filterNot { arg -> internalCompilerArguments.any { arg.contains(it) } }
            .joinToString(separator = " ") { "-$it" }

    private val internalCompilerArguments = setOf(
        "ir-output-dir",
        "ir-output-name",
        "Xallow-no-source-files",
        "Xir-module-name",
        "Xir-only",
        "Xir-produce-klib-dir",
        "Xir-per-module-output-name",
        "Xcommon-sources",
    )
}
