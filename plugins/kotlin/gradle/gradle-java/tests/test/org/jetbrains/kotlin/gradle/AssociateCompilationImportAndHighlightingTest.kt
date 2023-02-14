// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.facetSettings
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test
import java.io.PrintStream

class AssociateCompilationImportAndHighlightingTest3 : MultiplePluginVersionGradleImportingTestCase() {
    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20-dev-1816+")
    fun testAssociateCompilationIntegrationTest() {
        configureByFiles()
        importProject(false)

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

        val highlightingCheck = createHighlightingCheck(testLineMarkers = false)

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

    @Test
    @PluginTargetVersions(pluginVersion = "1.4+")
    fun testAssociateCompilationSourceSetVisibility() {
        configureByFiles()
        importProject(true)

        checkProjectStructure(false, false, false) {
            checkSingleTargetProject()
            checkTwoTargetProject()
            checkSkewedThreeTargetProjectWithFlatTestSourceSets()
            checkSkewedThreeTargetProjectWithEquivalentTestSourceSets()
            checkDiamondThreeTargetProjectWithFlatTestSourceSets()
            checkDiamondThreeTargetProjectWithEquivalentTestSourceSets()
        }
    }

    private fun ProjectInfo.checkSingleTargetProject() {
        module("root.p1.commonTest") {
            moduleDependency("root.p1.commonMain", DependencyScope.TEST)
            moduleDependency("root.p1.jvmMain", DependencyScope.TEST) // For as long as p1.commonTest remains a JVM source set
        }

        module("root.p1.jvmTest") {
            moduleDependency("root.p1.commonMain", DependencyScope.TEST)
            moduleDependency("root.p1.jvmMain", DependencyScope.TEST)
        }
    }

    private fun ProjectInfo.checkTwoTargetProject() {
        module("root.p2.commonTest") {
            moduleDependency("root.p2.commonMain", DependencyScope.TEST)
        }

        module("root.p2.jvmTest") {
            moduleDependency("root.p2.commonMain", DependencyScope.TEST)
            moduleDependency("root.p2.jvmMain", DependencyScope.TEST)
        }

        module("root.p2.linuxTest") {
            moduleDependency("root.p2.commonMain", DependencyScope.TEST)
            moduleDependency("root.p2.linuxMain", DependencyScope.TEST)
        }
    }

    private fun ProjectInfo.checkSkewedThreeTargetProjectWithFlatTestSourceSets() {
        module("root.p3.commonTest") {
            moduleDependency("root.p3.commonMain", DependencyScope.TEST)
        }

        module("root.p3.jsTest") {
            moduleDependency("root.p3.commonMain", DependencyScope.TEST)
            moduleDependency("root.p3.jsMain", DependencyScope.TEST)
        }

        module("root.p3.jvmTest") {
            moduleDependency("root.p3.commonMain", DependencyScope.TEST)
            moduleDependency("root.p3.concurrentMain", DependencyScope.TEST)
            moduleDependency("root.p3.jvmMain", DependencyScope.TEST)
        }

        module("root.p3.linuxTest") {
            moduleDependency("root.p3.commonMain", DependencyScope.TEST)
            moduleDependency("root.p3.concurrentMain", DependencyScope.TEST)
            moduleDependency("root.p3.linuxMain", DependencyScope.TEST)
        }
    }

    private fun ProjectInfo.checkSkewedThreeTargetProjectWithEquivalentTestSourceSets() {
        module("root.p4.commonTest") {
            moduleDependency("root.p4.commonMain", DependencyScope.TEST)
        }

        module("root.p4.concurrentTest") {
            moduleDependency("root.p4.commonMain", DependencyScope.TEST)
            moduleDependency("root.p4.concurrentMain", DependencyScope.TEST)
        }

        module("root.p4.jsTest") {
            moduleDependency("root.p4.commonMain", DependencyScope.TEST)
            moduleDependency("root.p4.jsMain", DependencyScope.TEST)
        }

        module("root.p4.jvmTest") {
            moduleDependency("root.p4.commonMain", DependencyScope.TEST)
            moduleDependency("root.p4.concurrentMain", DependencyScope.TEST)
            moduleDependency("root.p4.jvmMain", DependencyScope.TEST)
        }

        module("root.p4.linuxTest") {
            moduleDependency("root.p4.commonMain", DependencyScope.TEST)
            moduleDependency("root.p4.concurrentMain", DependencyScope.TEST)
            moduleDependency("root.p4.linuxMain", DependencyScope.TEST)
        }
    }

    private fun ProjectInfo.checkDiamondThreeTargetProjectWithFlatTestSourceSets() {
        module("root.p5.commonTest") {
            moduleDependency("root.p5.commonMain", DependencyScope.TEST)
        }

        module("root.p5.jsTest") {
            moduleDependency("root.p5.commonMain", DependencyScope.TEST)
            moduleDependency("root.p5.webMain", DependencyScope.TEST)
            moduleDependency("root.p5.jsMain", DependencyScope.TEST)
        }

        module("root.p5.jvmTest") {
            moduleDependency("root.p5.commonMain", DependencyScope.TEST)
            moduleDependency("root.p5.webMain", DependencyScope.TEST)
            moduleDependency("root.p5.concurrentMain", DependencyScope.TEST)
            moduleDependency("root.p5.jvmMain", DependencyScope.TEST)
        }

        module("root.p5.linuxTest") {
            moduleDependency("root.p5.commonMain", DependencyScope.TEST)
            moduleDependency("root.p5.concurrentMain", DependencyScope.TEST)
            moduleDependency("root.p5.linuxMain", DependencyScope.TEST)
        }
    }

    private fun ProjectInfo.checkDiamondThreeTargetProjectWithEquivalentTestSourceSets() {
        module("root.p6.commonTest") {
            moduleDependency("root.p6.commonMain", DependencyScope.TEST)
        }

        module("root.p6.webTest") {
            moduleDependency("root.p6.commonMain", DependencyScope.TEST)
            moduleDependency("root.p6.webMain", DependencyScope.TEST)
        }

        module("root.p6.concurrentTest") {
            moduleDependency("root.p6.commonMain", DependencyScope.TEST)
            moduleDependency("root.p6.concurrentMain", DependencyScope.TEST)
        }

        module("root.p6.jsTest") {
            moduleDependency("root.p6.commonMain", DependencyScope.TEST)
            moduleDependency("root.p6.webMain", DependencyScope.TEST)
            moduleDependency("root.p6.jsMain", DependencyScope.TEST)
        }

        module("root.p6.jvmTest") {
            moduleDependency("root.p6.commonMain", DependencyScope.TEST)
            moduleDependency("root.p6.webMain", DependencyScope.TEST)
            moduleDependency("root.p6.concurrentMain", DependencyScope.TEST)
            moduleDependency("root.p6.jvmMain", DependencyScope.TEST)
        }

        module("root.p6.linuxTest") {
            moduleDependency("root.p6.commonMain", DependencyScope.TEST)
            moduleDependency("root.p6.concurrentMain", DependencyScope.TEST)
            moduleDependency("root.p6.linuxMain", DependencyScope.TEST)
        }
    }

    override fun printOutput(stream: PrintStream, text: String) = stream.println(text)
}