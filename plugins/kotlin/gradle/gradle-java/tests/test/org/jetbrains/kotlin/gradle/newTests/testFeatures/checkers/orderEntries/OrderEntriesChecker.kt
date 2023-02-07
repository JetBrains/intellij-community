// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.orderEntries

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.*
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.gradle.newTests.TestConfiguration
import org.jetbrains.kotlin.gradle.newTests.workspace.PrinterContext
import org.jetbrains.kotlin.gradle.newTests.workspace.WorkspaceModelChecker
import org.jetbrains.kotlin.gradle.newTests.workspace.indented
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleInfos

object OrderEntriesChecker : WorkspaceModelChecker<OrderEntriesChecksConfiguration>() {
    override fun createDefaultConfiguration(): OrderEntriesChecksConfiguration = OrderEntriesChecksConfiguration()

    override val classifier: String = "dependencies"

    override fun renderTestConfigurationDescription(testConfiguration: TestConfiguration): List<String> {
        val configuration = testConfiguration.getConfiguration(OrderEntriesChecker)

        val hiddenStandardDependencies = buildList<String> {
            if (configuration.hideStdlib) add("stdlib")
            if (configuration.hideKotlinTest) add("kotlin-test")
            if (configuration.hideKonanDist) add("Kotlin/Native distribution")
            if (configuration.hideSdkDependency) add("sdk")
            if (configuration.hideSelfDependency) add("self")
        }

        return buildList {
            if (hiddenStandardDependencies.isNotEmpty())
                add("hiding following standard dependencies: ${hiddenStandardDependencies.joinToString()}")

            if (configuration.excludeDependencies != null) {
                add("hiding dependencies matching ${configuration.excludeDependencies.toString()}")
            }

            if (configuration.sortDependencies) {
                add("dependencies order is not checked")
            }
        }
    }

    override fun PrinterContext.process(module: Module) = with(printer) {
        val orderEntries = runReadAction { ModuleRootManager.getInstance(module).orderEntries }
        val filteredEntries = orderEntries.filterNot { shouldRemoveOrderEntry(it) }
        if (filteredEntries.isEmpty()) return

        val testConfig = testConfiguration.getConfiguration(OrderEntriesChecker)

        val dependsOnModules = module.implementedModules.toSet()
        val friendModules: Collection<ModuleInfo> = module.sourceModuleInfos.singleOrNull()
            ?.modulesWhoseInternalsAreVisible()
            .orEmpty()
            .toSet()

        val orderEntriesRendered = buildList {
            for (entry in filteredEntries) {
                val moduleInfos: Set<IdeaModuleInfo> = getModuleInfos(entry)
                val orderEntryModule = (entry as? ModuleOrderEntry)?.module
                add(
                    render(
                        orderEntry = entry,
                        isDependsOn = orderEntryModule in dependsOnModules,
                        isFriend = moduleInfos.isNotEmpty() && friendModules.containsAll(moduleInfos)
                    )
                )
            }
        }


        val orderEntriesFoldedIfNecessary = if (testConfig.hideKonanDist)
            orderEntriesRendered
        else
            foldKonanDist(orderEntriesRendered, module)

        val orderEntriesSortedIfNecessary = if (!testConfig.sortDependencies)
            orderEntriesFoldedIfNecessary
        else
            orderEntriesFoldedIfNecessary.sorted()

        indented {
            orderEntriesSortedIfNecessary.forEach { println(it) }
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
        val config = this.testConfiguration.getConfiguration(OrderEntriesChecker)

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
        return name in STDLIB_MODULES
                || name.startsWith(NATIVE_STDLIB_PREFIX_OLD_IMPORT)
                || name.startsWith(NATIVE_STDLIB_KGP_BASED_IMPORT)
    }

    private fun PrinterContext.isKotlinTestModule(orderEntry: OrderEntry): Boolean =
        presentableNameWithoutVersion(orderEntry) in KOTLIN_TEST_MODULES

    private fun isKonanDistModule(orderEntry: OrderEntry): Boolean {
        val name = orderEntry.presentableName
        // NB: we deliberately look for 'stdlib' substring instead of `!isStdlibModule(orderEntry)`
        // to make sure that if format of stdlib-entry unexpectedly changes, we won't mismatch it
        // with K/N Dist
        return "stdlib" !in name && NATIVE_DISTRIBUTION_LIBRARY_PATTERN.matches(name)
    }

    private fun PrinterContext.presentableNameWithoutVersion(orderEntry: OrderEntry): String =
        orderEntry.presentableName
            // Be careful not to use KGP_VERSION placeholder for 3rd-party libraries (e.g. those that try to align versioning with Kotlin)
            .let {
                if ("org.jetbrains.kotlin" in it || "Kotlin/Native" in it) it.replace(
                    kotlinGradlePluginVersion.toString(),
                    "{{KGP_VERSION}}"
                ) else it
            }
            .removePrefix("${project.name}.")
            .removePrefix("Gradle: ")

    private fun PrinterContext.getModuleInfos(orderEntry: OrderEntry): Set<IdeaModuleInfo> {
        when (orderEntry) {
            is ModuleOrderEntry -> return setOfNotNull(orderEntry.module?.toModuleInfo())

            is LibraryOrderEntry -> {
                val library = orderEntry.library ?: return emptySet()
                return runReadAction { LibraryInfoCache.getInstance(project)[library] }.toSet()
            }


            is JdkOrderEntry -> return setOf(SdkInfo(project, orderEntry.jdk ?: return emptySet()))

            else -> return emptySet()
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

    private val STDLIB_MODULES = setOf(
        "org.jetbrains.kotlin:kotlin-stdlib-common:{{KGP_VERSION}}",
        "org.jetbrains.kotlin:kotlin-stdlib-js:{{KGP_VERSION}}",
        "org.jetbrains.kotlin:kotlin-stdlib:{{KGP_VERSION}}",
        "org.jetbrains:annotations:13.0",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:{{KGP_VERSION}}",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:{{KGP_VERSION}}",
    )

    // Old import: Kotlin/Native {{KGP_VERSION}} - stdlib (PROVIDED)
    // KGP based import: Kotlin/Native: stdlib (COMPILE)
    private val NATIVE_STDLIB_PREFIX_OLD_IMPORT: String = "Kotlin/Native {{KGP_VERSION}} - stdlib"
    private val NATIVE_STDLIB_KGP_BASED_IMPORT: String = "Kotlin/Native: stdlib"

    private val KOTLIN_TEST_MODULES = setOf(
        "org.jetbrains.kotlin:kotlin-test-common:{{KGP_VERSION}}",
        "org.jetbrains.kotlin:kotlin-test-annotations-common:{{KGP_VERSION}}",

        "org.jetbrains.kotlin:kotlin-test-js:{{KGP_VERSION}}",

        "jetbrains.kotlin:kotlin-test:{{KGP_VERSION}}",
        "jetbrains.kotlin:kotlin-test-junit:{{KGP_VERSION}}",
        "hamcrest:hamcrest-core:1.3",
        "junit:4.13.2"
    )

    private val NATIVE_DISTRIBUTION_LIBRARY_PATTERN = "^Kotlin/Native.*".toRegex()
}
