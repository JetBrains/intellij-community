// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle

import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.kotlin.checkers.utils.clearTextFromDiagnosticMarkup
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

abstract class HierarchicalStructureByDefaultImportAndHighlightingTest : MultiplePluginVersionGradleImportingTestCase() {
    override fun testDataDirName(): String = "hierarchicalStructureByDefaultImportAndHighlighting"

    override fun clearTextFromMarkup(text: String): String = clearTextFromDiagnosticMarkup(text)

    class TestBucket : HierarchicalStructureByDefaultImportAndHighlightingTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.8.0+")
        fun testCommonKlibHighlighting() {
            configureByFiles()
            importProject()

            checkProjectStructure(exhaustiveModuleList = false, exhaustiveSourceSourceRootList = false, exhaustiveDependencyList = false) {
                module("commonKlibHighlighting.commonMain") {
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-serialization-core:commonMain:1.3.2", DependencyScope.COMPILE)
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-coroutines-core:commonMain:1.6.3", DependencyScope.COMPILE)
                }
                module("commonKlibHighlighting.commonTest") {
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-serialization-core:commonMain:1.3.2", DependencyScope.TEST)
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-coroutines-core:commonMain:1.6.3", DependencyScope.TEST)
                }
                module("commonKlibHighlighting.jvmMain") {
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.3.2", DependencyScope.COMPILE)
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.3", DependencyScope.COMPILE)
                }
                module("commonKlibHighlighting.jvmTest") {
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.3.2", DependencyScope.TEST)
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.3", DependencyScope.TEST)
                }
                module("commonKlibHighlighting.jsMain") {
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-serialization-core-js:1.3.2", DependencyScope.COMPILE)
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.6.3", DependencyScope.COMPILE)
                }
                module("commonKlibHighlighting.jsTest") {
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-serialization-core-js:1.3.2", DependencyScope.TEST)
                    libraryDependency("Gradle: org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.6.3", DependencyScope.TEST)
                }
            }

            checkHighlightingOnAllModules()
        }
    }
}