// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import org.jetbrains.kotlin.utils.Printer

interface WorkspaceModelPrinterContributor<T : ContributableEntity> {
    fun preprocess(elements: Collection<T>): Collection<T> = elements
    fun process(entity: T, printer: Printer)
}

interface ModulePrinterContributor : WorkspaceModelPrinterContributor<ModulePrinterEntity>
interface LibraryPrinterContributor : WorkspaceModelPrinterContributor<LibraryPrinterEntity>
interface SdkPrinterContributor : WorkspaceModelPrinterContributor<SdkPrinterEntity>

class CompositeWorkspaceModelPrinterContributor<T : ContributableEntity>(
    private val firstContributor: WorkspaceModelPrinterContributor<T>,
    private vararg val contributors: WorkspaceModelPrinterContributor<T>,
) : WorkspaceModelPrinterContributor<T> {
    override fun preprocess(elements: Collection<T>): Collection<T> =
        contributors.fold(firstContributor.preprocess(elements)) { lastResult, nextContributor -> nextContributor.preprocess(lastResult) }

    override fun process(entity: T, printer: Printer) {
        firstContributor.process(entity, printer)
        contributors.forEach { it.process(entity, printer) }
    }
}

internal fun <T : ContributableEntity> compositeContributor(
    vararg contributors: WorkspaceModelPrinterContributor<T>
): WorkspaceModelPrinterContributor<T>? {
    if (contributors.isEmpty()) return null

    val first = contributors.first()
    val rest = contributors.copyOfRange(1, contributors.size)

    return CompositeWorkspaceModelPrinterContributor(first, *rest)
}

class NoopModulePrinterContributor : ModulePrinterContributor {
    override fun process(entity: ModulePrinterEntity, printer: Printer) = Unit
}

class KotlinFacetSettingsPrinterContributor : ModulePrinterContributor {
    override fun process(entity: ModulePrinterEntity, printer: Printer) = with(printer) {
        val facetSettings = entity.kotlinFacetSettings ?: return

        indented {
            println("Settings from the Kotlin facet:")
            indented {
                println("External project ID: ${facetSettings.externalProjectId}")
                println("Language level: ${facetSettings.languageLevel}")
                println("API level: ${facetSettings.apiLevel}")
                println("MPP version: ${facetSettings.mppVersion?.name.orEmpty()}")
                println("dependsOn module names:")
                indented { facetSettings.dependsOnModuleNames.sorted().forEach(printer::println) }
                println("Additional visible module names:")
                indented { facetSettings.additionalVisibleModuleNames.sorted().forEach(printer::println) }
                println("Additional compiler arguments: ${facetSettings.compilerSettings?.additionalArguments.orEmpty()}")
            }
        }
    }
}

class SanitizingLibraryPrinterContributor : LibraryPrinterContributor {
    override fun preprocess(elements: Collection<LibraryPrinterEntity>): Collection<LibraryPrinterEntity> =
        replaceNativeDistributionLibraries(elements)
    override fun process(entity: LibraryPrinterEntity, printer: Printer) = Unit
}

class NoopSdkPrinterContributor : SdkPrinterContributor {
    override fun process(entity: SdkPrinterEntity, printer: Printer) = Unit
}

class SanitizingOrderEntryPrinterContributor : ModulePrinterContributor {

    override fun process(entity: ModulePrinterEntity, printer: Printer) = with(printer) {
        val orderEntries = entity.orderEntries
        if (orderEntries.isEmpty()) return

        val sanitizedEntries = replaceNativeDistributionOrderEntries(orderEntries)

        println("Order entries:")
        indented {
            for (entry in sanitizedEntries) {
                println(entry.presentableName)
            }
        }
    }
}
