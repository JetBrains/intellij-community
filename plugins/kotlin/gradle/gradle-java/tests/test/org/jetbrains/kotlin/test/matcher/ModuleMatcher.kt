// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.test.matcher

import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.systemIndependentPath
import org.jetbrains.kotlin.gradle.Reporter
import org.jetbrains.kotlin.gradle.toIoFile
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.test.domain.ModuleEntity
import java.io.File

class ModuleMatcher(val moduleEntity: ModuleEntity, val reporter: Reporter) : MatcherModel() {
    override fun report(message: String) {
        reporter.report(message)
    }

    fun languageVersion(expectedVersion: String) {
        checkReport("Language version", expectedVersion, moduleEntity.actualVersion)
    }

    fun libraryDependency(libraryName: Regex, scope: DependencyScope, isOptional: Boolean = false) {
        val libraryEntries = moduleEntity.orderEntries.filterIsInstance<LibraryOrderEntry>()
            .filter { it.libraryName?.matches(libraryName) == true }

        if (libraryEntries.size > 1) {
            report("Multiple root entries for library $libraryName")
        }

        if (!isOptional && libraryEntries.isEmpty()) {
            val candidate = moduleEntity.orderEntries
                .filterIsInstance<LibraryOrderEntry>()
                .sortedWith(Comparator { o1, o2 ->
                    val o1len = o1?.libraryName?.commonPrefixWith(libraryName.toString())?.length ?: 0
                    val o2len = o2?.libraryName?.commonPrefixWith(libraryName.toString())?.length ?: 0
                    o2len - o1len
                }).firstOrNull()

            val candidateName = candidate?.libraryName
            report("Expected library dependency $libraryName, found nothing. Most probably candidate: $candidateName")
        }

        // TODO: Add corresponding check for this
        //checkLibrary(libraryEntries.firstOrNull() ?: return, scope)
    }

    fun targetPlatform(vararg platforms: TargetPlatform) {
        val expected = platforms.flatMap { it.componentPlatforms }.toSet()
        val actual = moduleEntity.componentPlatforms

        if (actual == null) {
            report("Actual target platform is null")
            return
        }

        val notFound = expected.subtract(actual)
        if (notFound.isNotEmpty()) {
            report("These target platforms were not found: " + notFound.joinToString())
        }

        val unexpected = actual.subtract(expected)
        if (unexpected.isNotEmpty()) {
            report("Unexpected target platforms found: " + unexpected.joinToString())
        }
    }

    fun isHMPP(expectedValue: Boolean) {
        checkReport("isHMPP", expectedValue, moduleEntity.hMPPEnabled)
    }

    fun assertNoDependencyInBuildClasses() {
        val dependenciesInBuildDirectory = moduleEntity.orderEntries
            .flatMap { orderEntry ->
                orderEntry.getFiles(OrderRootType.SOURCES).toList().map { it.toIoFile() } +
                        orderEntry.getFiles(OrderRootType.CLASSES).toList().map { it.toIoFile() } +
                        orderEntry.getUrls(OrderRootType.CLASSES).toList().map { File(it) } +
                        orderEntry.getUrls(OrderRootType.SOURCES).toList().map { File(it) }
            }
            .map { file -> file.systemIndependentPath }
            .filter { path -> "/build/classes/" in path }

        if (dependenciesInBuildDirectory.isNotEmpty()) {
            report("References dependency in build directory:\n${dependenciesInBuildDirectory.joinToString("\n")}")
        }
    }

    // TDDO: Add remaining matcher methods
}