// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.facetSettings
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

class AssociateCompilationImportAndHighlightingTest3 : MultiplePluginVersionGradleImportingTestCase() {
    @Test
    @PluginTargetVersions(pluginVersion = "1.4+")
    fun testAssociateCompilationIntegrationTest() {
        configureByFiles()
        importProject(false)
        val highlightingCheck = createHighlightingCheck(testLineMarkers = false)

        facetSettings("project.p1.integrationTest").run {
            assertEquals(
                setOf(":p1:main"),
                additionalVisibleModuleNames
            )
        }

        facetSettings("project.p2.nativeTest").run {
            assertEquals(
                setOf(":p2:commonMain", ":p2:nativeMain"),
                additionalVisibleModuleNames
            )
        }

        checkProjectStructure(false, false, false) {

            module("project.p1.integrationTest") {
                highlightingCheck(module)
            }

            module("project.p1.main") {
                highlightingCheck(module)
            }

            module("project.p1.test") {
                highlightingCheck(module)
            }

            module("project.p2.nativeMain") {
                highlightingCheck(module)
            }

            module("project.p2.nativeTest") {
                highlightingCheck(module)
            }
        }
    }

}