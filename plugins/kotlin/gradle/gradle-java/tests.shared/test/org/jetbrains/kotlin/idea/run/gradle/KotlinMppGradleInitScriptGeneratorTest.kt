// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run.gradle

import com.intellij.openapi.project.modules
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils.listRepositories
import org.jetbrains.plugins.gradle.execution.build.GradleInitScriptGenerator
import org.jetbrains.plugins.gradle.execution.test.producer.GradleTestRunConfigurationProducerTestCase
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.Test

private const val PLUGIN_VERSION ="1.9.0"

class KotlinMppGradleInitScriptGeneratorTest : GradleTestRunConfigurationProducerTestCase() {
    override fun setUp() {
        super.setUp()
        generateAndImportMppProject()
    }

    @Test
    fun testInitScriptGenerator() {
        val module = myTestFixture.project.modules.find { it.name == "project.jvmMain" }
        assertNotNull(module)
        val scriptGenerator = GradleInitScriptGenerator.findGenerator(module!!)
        assertNotNull(scriptGenerator)
    }

    private fun generateAndImportMppProject() {
        createSettingsFile {
            setProjectName("project")
        }

        createBuildFile {
            withPlugin("org.jetbrains.kotlin.multiplatform", PLUGIN_VERSION)
            withPrefix {
                code(
                    """
                repositories {
                    ${listRepositories(false, gradleVersion.version)}                    
                }
                """.trimIndent()
                )
            }

            withPostfix {
                code(
                    """
                kotlin {
                    jvm {
                        compilations.all {
                            kotlinOptions.jvmTarget = '17'
                        }
                        withJava()
                        testRuns["test"].executionTask.configure {
                            useJUnitPlatform()
                        }
                    }
                    
                    sourceSets {
                        commonMain { }
                        commonTest {
                            dependencies {
                                implementation kotlin('test')
                            }
                        }
                        jvmMain { }
                        jvmTest { }
                    }
                }
            """.trimIndent()
                )
            }
        }

        importProject()
        assertModulesContains(
            "project.jvmMain",
            "project.jvmTest",
            "project.commonMain",
            "project.commonTest"
        )
    }
}