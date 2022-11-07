// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ucache.KOTLIN_SCRIPTS_AS_ENTITIES
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runners.Parameterized

abstract class GradleBuildFileHighlightingTest : KotlinGradleImportingTestCase() {

    companion object {
        private val GRADLE_VERSION_AND_SCRIPT_FLAG = SUPPORTED_GRADLE_VERSIONS
            .map { listOf(arrayOf(it, false), arrayOf(it, true)) }
            .flatten()

        @JvmStatic
        @Suppress("ACCIDENTAL_OVERRIDE")
        @Parameterized.Parameters(name = "{index}: with Gradle-{0}, scriptsAsEntities-{1}")
        fun testInputData(): List<Array<out Any>> = GRADLE_VERSION_AND_SCRIPT_FLAG
    }


    @JvmField
    @Parameterized.Parameter(1)
    var scriptsAsEntities: Boolean? = null

    @JvmField
    @Rule
    val setRegistryFlag = RegistryFlagRule()

    inner class RegistryFlagRule : ExternalResource() {
        override fun before() {
            Registry.get(KOTLIN_SCRIPTS_AS_ENTITIES).setValue(scriptsAsEntities!!)
        }

        override fun after() {
            Registry.get(KOTLIN_SCRIPTS_AS_ENTITIES).resetToDefault()
        }
    }


    class KtsInJsProject2114 : GradleBuildFileHighlightingTest() {
        @TargetVersions("4.8 <=> 6.0")
        @Test
        fun testKtsInJsProject() {
            val buildGradleKts = configureByFiles().findBuildGradleKtsFile()
            importProjectUsingSingeModulePerGradleProject()
            checkHighlighting(buildGradleKts)
        }
    }

    class Simple : GradleBuildFileHighlightingTest() {
        @TargetVersions("5.3+")
        @Test
        fun testSimple() {
            val buildGradleKts = configureByFiles().findBuildGradleKtsFile()
            importProjectUsingSingeModulePerGradleProject()
            checkHighlighting(buildGradleKts)
        }
    }

    class ComplexBuildGradleKts : GradleBuildFileHighlightingTest() {
        @Ignore
        @TargetVersions("4.8+")
        @Test
        fun testComplexBuildGradleKts() {
            val buildGradleKts = configureByFiles().findBuildGradleKtsFile()
            importProjectUsingSingeModulePerGradleProject()
            checkHighlighting(buildGradleKts)
        }

    }

    class JavaLibraryPlugin14 : GradleBuildFileHighlightingTest() {
        @Test
        @TargetVersions("6.0.1+")
        fun testJavaLibraryPlugin() {
            val buildGradleKts = configureByFiles().findBuildGradleKtsFile()
            importProject()

            checkHighlighting(buildGradleKts)
        }

    }


    protected fun List<VirtualFile>.findBuildGradleKtsFile(): VirtualFile {
        return singleOrNull { it.name == GradleConstants.KOTLIN_DSL_SCRIPT_NAME }
            ?: error("Couldn't find any build.gradle.kts file")
    }

    protected fun checkHighlighting(file: VirtualFile) {
        runInEdtAndWait {
            runReadAction {
                val psiFile = PsiManager.getInstance(myProject).findFile(file) as? KtFile
                    ?: error("Couldn't find psiFile for virtual file: ${file.canonicalPath}")

                ScriptConfigurationManager.updateScriptDependenciesSynchronously(psiFile)

                val bindingContext = psiFile.analyzeWithContent()
                val diagnostics = bindingContext.diagnostics.filter { it.severity == Severity.ERROR }

                assert(diagnostics.isEmpty()) {
                    val diagnosticLines = diagnostics.joinToString("\n") { DefaultErrorMessages.render(it) }
                    "Diagnostic list should be empty:\n $diagnosticLines"
                }
            }
        }
    }

    override fun testDataDirName() = "highlighting"
}