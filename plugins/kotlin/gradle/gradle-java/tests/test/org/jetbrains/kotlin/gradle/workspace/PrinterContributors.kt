// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEntry
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.utils.Printer

interface WorkspaceModelPrinterContributor<T : ContributableEntity> {
    fun process(entity: T, printer: Printer)
}

interface ModulePrinterContributor : WorkspaceModelPrinterContributor<ModulePrinterEntity>

class CompositeWorkspaceModelPrinterContributor<T : ContributableEntity>(
    private val firstContributor: WorkspaceModelPrinterContributor<T>,
    private vararg val contributors: WorkspaceModelPrinterContributor<T>,
) : WorkspaceModelPrinterContributor<T> {
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
        if (entity !is ModulePrinterEntityImpl) return // synthetic entity, skip
        val facetSettings = runReadAction { KotlinFacetSettingsProvider.getInstance(entity.module.project)
            ?.getSettings(entity.module) }
            ?: return

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

class SanitizingOrderEntryPrinterContributor : ModulePrinterContributor {

    override fun process(entity: ModulePrinterEntity, printer: Printer) = with(printer) {
        if (entity !is ModulePrinterEntityImpl) return // synthetic entity, skip
        val orderEntries = runReadAction { ModuleRootManager.getInstance(entity.module).orderEntries }
            .map(OrderEntry::toPrinterEntity)
        val sanitizedEntries = replaceNativeDistributionOrderEntries(orderEntries)

        if (sanitizedEntries.isEmpty()) return

        println("Order entries:")
        indented {
            for (entry in sanitizedEntries) {
                println(entry.presentableName)
            }
        }
    }
}
