// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import java.io.File

class KotlinFacetSettingsPrinterContributor : ModulePrinterContributor {
    override fun PrinterContext.process(module: Module) = with(printer) {
        val facetSettings = runReadAction {
          KotlinFacetSettingsProvider.getInstance(module.project)
            ?.getSettings(module)
        } ?: return

        indented {
            println("Settings from the Kotlin facet:")
            indented {
                println("Target platform: ${module.platform}")
                println("External project ID: ${facetSettings.externalProjectId}")
                println("Language level: ${facetSettings.languageLevel}")
                println("API level: ${facetSettings.apiLevel}")
                println("MPP version: ${facetSettings.mppVersion?.name.orEmpty()}")
                println("dependsOn module names:")
                indented { facetSettings.dependsOnModuleNames.sorted().forEach(printer::println) }
                println("Additional visible module names:")
                indented { facetSettings.additionalVisibleModuleNames.sorted().forEach(printer::println) }

                val additionalArguments = facetSettings.compilerSettings?.additionalArguments?.removeAbsolutePaths(projectRoot)
                println("Additional compiler arguments: ${additionalArguments.orEmpty()}")
            }
        }
    }

    private fun String.removeAbsolutePaths(projectRoot: File): String = replace(projectRoot.toString(), "")
}
