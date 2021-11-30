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
import org.jetbrains.plugins.gradle.frameworkSupport.script.GroovyScriptBuilder
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder.Companion.buildscript
import org.jetbrains.plugins.gradle.util.findChildByType
import org.jetbrains.plugins.gradle.util.runReadActionAndWait
import org.junit.Test

class GradleMppRunConfigurationProducersTest : GradleTestRunConfigurationProducerTestCase() {

    private lateinit var projectData: ProjectData


    override fun setUp() {
        super.setUp()
        projectData = generateAndImportTemplateMppProject()
    }

    //// ALL IN PACKAGE /////

    @Test
    fun allTestsInJsPackage() {
        assertConfigurationFromContext<KotlinMultiplatformAllInPackageConfigurationProducer>(
            """:cleanJsLegacyBrowserTest :jsLegacyBrowserTest --tests "org.jetbrains.*"""",
            runReadActionAndWait {
                val jetBrainsDir = projectData["project.jsTest"]["org.jetbrains.JsTests"].element.containingFile.containingDirectory
                jetBrainsDir
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
    fun allTestsInNativePackage() {
        assertConfigurationFromContext<KotlinMultiplatformAllInPackageConfigurationProducer>(
            """:cleanNativeTest :nativeTest --tests "org.jetbrains.*"""",
            runReadActionAndWait {
                val jetBrainsDir =
                    projectData["project.nativeTest"]["org.jetbrains.NativeTests"].element.containingFile.containingDirectory
                jetBrainsDir
            }
        )
    }

    //// ALL IN DIRECTORY /////

    @Test
    fun allTestsInJsDirectory() {
        assertConfigurationFromContext<KotlinMultiplatformAllInDirectoryConfigurationProducer>(
            """:cleanJsLegacyBrowserTest :jsLegacyBrowserTest""",
            runReadActionAndWait {
                val jetBrainsDir = projectData["project.jsTest"]["org.jetbrains.JsTests"].element.containingFile.containingDirectory
                val kotlinDir = jetBrainsDir.parentDirectory?.parentDirectory!!
                kotlinDir
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
    fun allTestsInNativeDirectory() {
        assertConfigurationFromContext<KotlinMultiplatformAllInDirectoryConfigurationProducer>(
            """:cleanNativeTest :nativeTest""",
            runReadActionAndWait {
                val jetBrainsDir =
                    projectData["project.nativeTest"]["org.jetbrains.NativeTests"].element.containingFile.containingDirectory
                val kotlinDir = jetBrainsDir.parentDirectory?.parentDirectory!!
                kotlinDir
            }
        )
    }

    //// ALL IN MODULE /////

    @Test
    fun allTestsInJsModule() {
        assertConfigurationFromContext<KotlinMultiplatformAllInDirectoryConfigurationProducer>(
            """:cleanJsLegacyBrowserTest :jsLegacyBrowserTest""",
            runReadActionAndWait {
                val jetBrainsDir = projectData["project.jsTest"]["org.jetbrains.JsTests"].element.containingFile.containingDirectory
                val kotlinDir = jetBrainsDir.parentDirectory?.parentDirectory
                val moduleDirectory = kotlinDir?.parentDirectory
                moduleDirectory!!
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

    @Test
    fun allTestsInNativeModule() {
        assertConfigurationFromContext<KotlinMultiplatformAllInDirectoryConfigurationProducer>(
            """:cleanNativeTest :nativeTest""",
            runReadActionAndWait {
                val jetBrainsDir =
                    projectData["project.nativeTest"]["org.jetbrains.NativeTests"].element.containingFile.containingDirectory
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

    private fun generateAndImportTemplateMppProject(): ProjectData {
        val jsTestsFile = createProjectSubFile(
            "src/jsTest/kotlin/org/jetbrains/JsTests.kt", """ 
        package org.jetbrains
        
        import kotlin.test.Test

        class JsTests { }
            """.trimIndent()
        )

        val jvmTestSuiteFile = createProjectSubFile(
            "src/jvmTest/kotlin/org/jetbrains/JsTests.kt", """ 
        package org.jetbrains
        
        import kotlin.test.Test

        class JvmTests { }
            """.trimIndent()
        )

        val nativeTestSuiteFile = createProjectSubFile(
            "src/nativeTest/kotlin/org/jetbrains/NativeTests.kt", """ 
        package org.jetbrains
        
        import kotlin.test.Test

        class NativeTests { }
            """.trimIndent()
        )

        createProjectSubFile("settings.gradle", GroovyScriptBuilder.groovy {
            assign("rootProject.name", "project")
        })

        createProjectSubFile("build.gradle", buildscript {
            withPlugin("org.jetbrains.kotlin.multiplatform", TestedKotlinGradlePluginVersions.ALL_PUBLIC.last().toString())
            withPrefix {
                code("""
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
                    js(BOTH) {
                        browser {
                            commonWebpackConfig {
                                cssSupport.enabled = true
                            }
                        }
                    }
                    
                    def hostOs = System.getProperty("os.name")
                    def isMingwX64 = hostOs.startsWith("Windows")
                    def nativeTarget
                    if (hostOs == "Mac OS X") nativeTarget = macosX64('native')
                    else if (hostOs == "Linux") nativeTarget = linuxX64("native")
                    else if (isMingwX64) nativeTarget = mingwX64("native")
                    else throw new GradleException("Host OS is not supported in Kotlin/Native.")
                    
                    sourceSets {
                        commonMain { }
                        commonTest {
                            dependencies {
                                implementation kotlin('test')
                            }
                        }
                        jvmMain { }
                        jvmTest { }
                        jsMain { }
                        jsTest { }
                        nativeMain { }
                        nativeTest { }
                    }
                }
            """.trimIndent()
                )
            }
        })

        importProject()
        assertModulesContains(
            "project.jsMain",
            "project.jsTest",
            "project.jvmMain",
            "project.jvmTest",
            "project.nativeMain",
            "project.nativeTest",
            "project.commonMain",
            "project.commonTest"
        )
        val projectDir = findPsiDirectory(".")

        val jsTestModuleDir = findPsiDirectory("src/jsTest")
        val jsTestModuleTesSuite = extractClassData(jsTestsFile)

        val jvmTestModuleDir = findPsiDirectory("src/jvmTest")
        val jvmTestModuleTesSuite = extractClassData(jvmTestSuiteFile)

        val nativeTestModuleDir = findPsiDirectory("src/nativeTest")
        val nativeTestModuleTesSuite = extractClassData(nativeTestSuiteFile)

        return ProjectData(
            ModuleData("project", projectDir),
            ModuleData("project.jsTest", jsTestModuleDir, jsTestModuleTesSuite),
            ModuleData("project.jvmTest", jvmTestModuleDir, jvmTestModuleTesSuite),
            ModuleData("project.nativeTest", nativeTestModuleDir, nativeTestModuleTesSuite)
        )
    }
}