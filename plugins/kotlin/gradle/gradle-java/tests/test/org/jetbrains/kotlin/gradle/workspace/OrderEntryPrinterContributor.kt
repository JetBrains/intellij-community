// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ExportableOrderEntry
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEntry
import org.jetbrains.kotlin.gradle.newTests.testFeatures.OrderEntriesFilteringTestFeature

internal class OrderEntryPrinterContributor : ModulePrinterContributor {
    override fun PrinterContext.process(module: Module) = with(printer) {
        val orderEntries = runReadAction { ModuleRootManager.getInstance(module).orderEntries }
        val filteredEntries = orderEntries.filterNot { shouldRemoveOrderEntry(it) }
        if (filteredEntries.isEmpty()) return

        indented {
            for (entry in filteredEntries) {
                println(entry.render())
            }
        }
    }

    private fun OrderEntry.render(): String {
        val exportableOrderEntryPostfix = if (this is ExportableOrderEntry) " (${scope.name})" else ""
        return "${presentableName}$exportableOrderEntryPostfix"
    }

    private fun PrinterContext.shouldRemoveOrderEntry(entry: OrderEntry): Boolean {
        val config = this.testConfiguration.getConfiguration(OrderEntriesFilteringTestFeature)

        // NB: so far, we hide self-dependencies and SDK dependencies unconditionally
        return config.hideSdkDependency && isSelfDependency(entry) ||
                config.hideSdkDependency && isSdkDependency(entry) ||
                config.hideStdlib && isStdlibModule(entry) ||
                config.hideKotlinTest && isKotlinTestModule(entry) ||
                config.hideKonanDist && isKonanDistModule(entry)
    }

    // In IJ workspace model, each source module should have a dependency on itself
    private fun isSelfDependency(orderEntry: OrderEntry): Boolean =
        orderEntry.presentableName == "<Module source>"

    private fun isSdkDependency(orderEntry: OrderEntry): Boolean =
        orderEntry is JdkOrderEntry

    private fun PrinterContext.isStdlibModule(orderEntry: OrderEntry): Boolean =
        presentableNameWithoutVersion(orderEntry) in STDLIB_MODULES

    private fun PrinterContext.isKotlinTestModule(orderEntry: OrderEntry): Boolean =
        presentableNameWithoutVersion(orderEntry) in KOTLIN_TEST_MODULES

    private fun isKonanDistModule(orderEntry: OrderEntry): Boolean = NATIVE_DISTRIBUTION_LIBRARY_PATTERN.matches(orderEntry.presentableName)

    private fun PrinterContext.presentableNameWithoutVersion(orderEntry: OrderEntry): String =
        orderEntry.presentableName.replace(kotlinGradlePluginVersion.toString(), "{{KGP_VERSION}}")

    companion object {
        private val STDLIB_MODULES = setOf(
            "Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:{{KGP_VERSION}}",
            "Gradle: org.jetbrains.kotlin:kotlin-stdlib-js:{{KGP_VERSION}}",
            "Gradle: org.jetbrains.kotlin:kotlin-stdlib:{{KGP_VERSION}}",
            "Gradle: org.jetbrains:annotations:13.0",
            "Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk8:{{KGP_VERSION}}",
            "Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk7:{{KGP_VERSION}}"
        )

        private val KOTLIN_TEST_MODULES = setOf(
            "Gradle: org.jetbrains.kotlin:kotlin-test-common:{{KGP_VERSION}}",
            "Gradle: org.jetbrains.kotlin:kotlin-test-annotations-common:{{KGP_VERSION}}",

            "Gradle: org.jetbrains.kotlin:kotlin-test-js:{{KGP_VERSION}}",

            "Gradle: org.jetbrains.kotlin:kotlin-test:{{KGP_VERSION}}",
            "Gradle: org.jetbrains.kotlin:kotlin-test-junit:{{KGP_VERSION}}",
            "Gradle: org.hamcrest:hamcrest-core:1.3",
            "Gradle: junit:junit:4.13.2"
        )

        private val NATIVE_DISTRIBUTION_LIBRARY_PATTERN = "^Kotlin/Native.*".toRegex()
    }
}
