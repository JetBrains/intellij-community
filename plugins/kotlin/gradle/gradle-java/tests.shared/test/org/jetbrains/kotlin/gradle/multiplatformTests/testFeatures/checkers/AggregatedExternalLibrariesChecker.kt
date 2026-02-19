// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager.getInstance
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractTestChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinSyncTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.findMostSpecificExistingFileOrNewDefault
import kotlin.test.assertEquals

object AggregatedExternalLibrariesChecker : AbstractTestChecker<AggregatedExternalLibrariesConfiguration>() {

    override fun createDefaultConfiguration(): AggregatedExternalLibrariesConfiguration = AggregatedExternalLibrariesConfiguration()

    override fun KotlinSyncTestsContext.check() {
        val expectedFile = findMostSpecificExistingFileOrNewDefault("aggregatedExternalLibraries")
        val expectedContent = expectedFile.takeIf { it.exists() }
            ?.readText()
            ?: error("Expected file does not exist")
        val projectLibraries = runReadAction<List<@Nls(capitalization = Nls.Capitalization.Title) String>> {
            ModuleManager.getInstance(testProject).modules.flatMap<Module, @Nls(capitalization = Nls.Capitalization.Title) String> { module ->
                getInstance(module).orderEntries
                    .filterIsInstance<LibraryOrderEntry>()
                    .map<LibraryOrderEntry, @Nls(capitalization = Nls.Capitalization.Title) String> { it.presentableName }
            }
        }
        val filteredLibraries = projectLibraries
            .filterNot<@Nls(capitalization = Nls.Capitalization.Title) String> { it.contains("org.jetbrains") }
            .distinct<@Nls(capitalization = Nls.Capitalization.Title) String>()
            .sorted()
            .joinToString("\n")
        assertEquals(expectedContent, filteredLibraries, "Library dependencies do not match the expected content!")
    }

}

class AggregatedExternalLibrariesConfiguration
