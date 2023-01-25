// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.*
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.gradle.newTests.testFeatures.OrderEntriesFilteringTestFeature
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos

internal class OrderEntryPrinterContributor : ModulePrinterContributor {
    override fun PrinterContext.process(module: Module) = with(printer) {
        val orderEntries = runReadAction { ModuleRootManager.getInstance(module).orderEntries }
        val filteredEntries = orderEntries.filterNot { shouldRemoveOrderEntry(it) }
        if (filteredEntries.isEmpty()) return

        val dependsOnModules = module.implementedModules.toSet()
        val friendModules: Collection<ModuleInfo> = module.sourceModuleInfos.singleOrNull()
            ?.modulesWhoseInternalsAreVisible()
            .orEmpty()

        val orderEntriesRendered = buildList {
            for (entry in filteredEntries) {
                val moduleInfo: IdeaModuleInfo? = getModuleInfo(entry)
                val orderEntryModule = (entry as? ModuleOrderEntry)?.module
                add(
                    render(
                        orderEntry = entry,
                        isDependsOn = orderEntryModule in dependsOnModules,
                        isFriend = friendModules.contains<ModuleInfo?>(moduleInfo)
                    )
                )
            }
        }

        val orderEntriesFoldedIfNecessary = if (testConfiguration.getConfiguration(OrderEntriesFilteringTestFeature).hideKonanDist)
            orderEntriesRendered
        else
            foldKonanDist(orderEntriesRendered, module)

        indented {
            orderEntriesFoldedIfNecessary.forEach { println(it) }
        }
    }

    private fun PrinterContext.render(orderEntry: OrderEntry, isDependsOn: Boolean, isFriend: Boolean): String {
        val modifiers = buildList<String> {
            if (isDependsOn) add("refines")
            if (isFriend) add("friend")
            if (orderEntry is ExportableOrderEntry) add(orderEntry.scope.name)
        }

        val renderedModifiersIfAny = if (modifiers.isNotEmpty())
            modifiers.joinToString(prefix = " (", postfix = ")")
        else
            ""
        return "${presentableNameWithoutVersion(orderEntry)}$renderedModifiersIfAny"
    }

    private fun PrinterContext.shouldRemoveOrderEntry(entry: OrderEntry): Boolean {
        val config = this.testConfiguration.getConfiguration(OrderEntriesFilteringTestFeature)

        // NB: so far, we hide self-dependencies and SDK dependencies unconditionally
        return config.hideSdkDependency && isSelfDependency(entry) ||
                config.hideSdkDependency && isSdkDependency(entry) ||
                config.hideStdlib && isStdlibModule(entry) ||
                config.hideKotlinTest && isKotlinTestModule(entry) ||
                config.hideKonanDist && isKonanDistModule(entry) ||
                config.excludeDependencies != null && config.excludeDependencies!!.matches(entry.presentableName) ||
                config.onlyDependencies != null && !config.onlyDependencies!!.matches(entry.presentableName)
    }

    // In IJ workspace model, each source module should have a dependency on itself
    private fun isSelfDependency(orderEntry: OrderEntry): Boolean =
        orderEntry.presentableName == "<Module source>"

    private fun isSdkDependency(orderEntry: OrderEntry): Boolean =
        orderEntry is JdkOrderEntry

    private fun PrinterContext.isStdlibModule(orderEntry: OrderEntry): Boolean {
        val name = presentableNameWithoutVersion(orderEntry)
        return name in STDLIB_MODULES || name.startsWith(NATIVE_STDLIB_PREFIX)
    }

    private fun PrinterContext.isKotlinTestModule(orderEntry: OrderEntry): Boolean =
        presentableNameWithoutVersion(orderEntry) in KOTLIN_TEST_MODULES

    private fun PrinterContext.isKonanDistModule(orderEntry: OrderEntry): Boolean =
        !isStdlibModule(orderEntry) && NATIVE_DISTRIBUTION_LIBRARY_PATTERN.matches(orderEntry.presentableName)

    private fun PrinterContext.presentableNameWithoutVersion(orderEntry: OrderEntry): String =
        orderEntry.presentableName.replace(kotlinGradlePluginVersion.toString(), "{{KGP_VERSION}}")

    private fun PrinterContext.getModuleInfo(orderEntry: OrderEntry): IdeaModuleInfo? {
        when (orderEntry) {
            is ModuleOrderEntry -> return orderEntry.module?.toModuleInfo()

            is LibraryOrderEntry -> {
                val library = orderEntry.library ?: return null
                val libraryInfos = runReadAction { LibraryInfoCache.getInstance(project)[library] }

                if (libraryInfos.size > 1)
                    error(
                        "Unexpectedly got several LibraryInfos for one LibraryOrderEntry\n" +
                                "LibraryOrderEntry = ${library.presentableName}\n" +
                                "LibraryInfos = ${libraryInfos.joinToString { it.displayedName }}"
                    )

                return libraryInfos.firstOrNull()
            }


            is JdkOrderEntry -> return SdkInfo(project, orderEntry.jdk ?: return null)

            else -> return null
        }
    }

    private fun Module.toModuleInfo(): IdeaModuleInfo? {
        val sourceModuleInfos = sourceModuleInfos
        check(sourceModuleInfos.size <= 1) {
            "Unexpected multiple module infos for module ${this.name}\n" +
                    "This can happen if main/test are imported as source roots of\n" +
                    "single Module, instead of being imported as two different Modules\n" +
                    "This configuration is not expected in MPP environment (and tests)"
        }
        return sourceModuleInfos.singleOrNull()
    }



    companion object {
        private val STDLIB_MODULES = setOf(
            "Gradle: org.jetbrains.kotlin:kotlin-stdlib-common:{{KGP_VERSION}}",
            "Gradle: org.jetbrains.kotlin:kotlin-stdlib-js:{{KGP_VERSION}}",
            "Gradle: org.jetbrains.kotlin:kotlin-stdlib:{{KGP_VERSION}}",
            "Gradle: org.jetbrains:annotations:13.0",
            "Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk8:{{KGP_VERSION}}",
            "Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk7:{{KGP_VERSION}}",
        )
        // K/N stdlib has suffix of platform, so plain contains wouldn't work
        private val NATIVE_STDLIB_PREFIX: String = "Kotlin/Native {{KGP_VERSION}} - stdlib"

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
