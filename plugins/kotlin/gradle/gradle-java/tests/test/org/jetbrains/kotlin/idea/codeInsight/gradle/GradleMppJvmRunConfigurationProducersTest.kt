// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils.TestedKotlinGradlePluginVersions
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKotlinTestUtils.listRepositories
import org.jetbrains.kotlin.idea.gradleJava.testing.KotlinMultiplatformAllInDirectoryConfigurationProducer
import org.jetbrains.kotlin.idea.gradleJava.testing.KotlinMultiplatformAllInPackageConfigurationProducer
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducerTestCase
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer
import org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer
import org.jetbrains.plugins.gradle.frameworkSupport.script.GroovyScriptBuilder
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder.Companion.buildscript
import org.jetbrains.plugins.gradle.util.findChildByType
import org.jetbrains.plugins.gradle.util.runReadActionAndWait
import org.junit.Test

/**
 * See paired [GradleMppNoJvmRunConfigurationProducersTest216]
 */
class GradleMppJvmRunConfigurationProducersTest4 : GradleTestRunConfigurationProducerTestCase() {
    private lateinit var projectData: ProjectData

    override fun setUp() {
        super.setUp()
        projectData = generateAndImportMppProject()
    }

    @Test
    fun allTestsInJvmClass() {
        assertConfigurationFromContext<TestClassGradleConfigurationProducer>(
            """:cleanJvmTest :jvmTest --tests "org.jetbrains.JvmTests"""",
            runReadActionAndWait {
                val psiClass = projectData["project.jvmTest"]["org.jetbrains.JvmTests"].element
                psiClass
            }
        )
    }

    @Test
    fun testForJvmMethod() {
        assertConfigurationFromContext<TestMethodGradleConfigurationProducer>(
            """:cleanJvmTest :jvmTest --tests "org.jetbrains.JvmTests.jvmTest"""",
            runReadActionAndWait {
                val psiMethod = projectData["project.jvmTest"]["org.jetbrains.JvmTests"]["jvmTest"].element
                psiMethod
            }
        )
    }

    @Test
    fun allTestsInJvmPackage() {
        assertConfigurationFromContext<KotlinMultiplatformAllInPackageConfigurationProducer>(
            """:cleanJvmTest :jvmTest --tests "org.jetbrains.*"""",
            runReadActionAndWait {
                val jetBrainsDir = projectData["project.jvmTest"]["org.jetbrains.JvmTests"].element.containingFile.containingDirectory
                jetBrainsDir
            }
        )
    }

    @Test
    fun allTestsInJvmDirectory() {
        assertConfigurationFromContext<KotlinMultiplatformAllInDirectoryConfigurationProducer>(
            """:cleanJvmTest :jvmTest""",
            runReadActionAndWait {
                val jetBrainsDir = projectData["project.jvmTest"]["org.jetbrains.JvmTests"].element.containingFile.containingDirectory
                val kotlinDir = jetBrainsDir.parentDirectory?.parentDirectory!!
                kotlinDir
            }
        )
    }

    @Test
    fun allTestsInJvmModule() {
        assertConfigurationFromContext<KotlinMultiplatformAllInDirectoryConfigurationProducer>(
            """:cleanJvmTest :jvmTest""",
            runReadActionAndWait {
                val jetBrainsDir = projectData["project.jvmTest"]["org.jetbrains.JvmTests"].element.containingFile.containingDirectory
                val kotlinDir = jetBrainsDir.parentDirectory?.parentDirectory
                val moduleDirectory = kotlinDir?.parentDirectory
                moduleDirectory!!
            }
        )
    }

    override fun extractClassData(file: VirtualFile) = runReadActionAndWait {
        val psiManager = PsiManager.getInstance(myProject)
        val psiFile = psiManager.findFile(file)!!
        val ktClass = psiFile.findChildByType<KtClass>().toLightClass() ?: throw RuntimeException("Couldn't extract Kotlin class")
        val psiMethods = ktClass.methods
        val methods = psiMethods.map { MethodData(it.name, it) }
        ClassData(ktClass.qualifiedName!!, ktClass, methods)
    }

    private fun generateAndImportMppProject(): ProjectData {
        val jvmTestSuiteFile = createProjectSubFile(
            "src/jvmTest/kotlin/org/jetbrains/JsTests.kt", """ 
        package org.jetbrains
        
        import kotlin.test.Test

        class JvmTests {
            @Test
            fun jvmTest() {}
        }
            """.trimIndent()
        )

        createProjectSubFile("settings.gradle", GroovyScriptBuilder.groovy {
            assign("rootProject.name", "project")
        })

        createProjectSubFile("build.gradle", buildscript {
            withPlugin("org.jetbrains.kotlin.multiplatform", TestedKotlinGradlePluginVersions.ALL_PUBLIC.last().toString())
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
                            kotlinOptions.jvmTarget = '1.8'
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
        })

        importProject()
        assertModulesContains(
            "project.jvmMain",
            "project.jvmTest",
            "project.commonMain",
            "project.commonTest"
        )
        val projectDir = findPsiDirectory(".")

        val jvmTestModuleDir = findPsiDirectory("src/jvmTest")
        val jvmTestModuleTesSuite = extractClassData(jvmTestSuiteFile)

        return ProjectData(
            ModuleData("project", projectDir),
            ModuleData("project.jvmTest", jvmTestModuleDir, jvmTestModuleTesSuite),
        )
    }
}