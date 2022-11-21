// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEntry
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.utils.Printer

interface ModulePrinterContributor {
    fun process(module: Module, printer: Printer)
}

class NoopModulePrinterContributor : ModulePrinterContributor {
    override fun process(module: Module, printer: Printer) = Unit
}

class KotlinFacetSettingsPrinterContributor : ModulePrinterContributor {
    override fun process(module: Module, printer: Printer) = with(printer) {
        val facetSettings = runReadAction { KotlinFacetSettingsProvider.getInstance(module.project)
            ?.getSettings(module) }
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
    override fun process(module: Module, printer: Printer) = with(printer) {
        val orderEntries = runReadAction { ModuleRootManager.getInstance(module).orderEntries }
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
