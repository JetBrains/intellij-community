// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.highlighter.KotlinProblemHighlightFilter
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.extensions.cloneWithCorruptedRoots
import org.jetbrains.plugins.gradle.extensions.rootsFiles
import org.jetbrains.plugins.gradle.extensions.rootsUrls
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Ignore
import org.junit.Test

abstract class GradleBuildFileHighlightingTest : KotlinGradleImportingTestCase() {

    protected fun withHighLightingFilterChecked(file: VirtualFile, block: () -> Unit) = runBlocking {
        val psiFile = readAction {
            PsiManager.getInstance(myProject).findFile(file) as? KtFile
                ?: error("Couldn't get PSI for $file")
        }

        val highlightFilter = KotlinProblemHighlightFilter()

        val highlightBefore = readAction { highlightFilter.shouldHighlight(psiFile) }
        assertFalse("Script shouldn't be highlighted before the import is over", highlightBefore)

        block.invoke()

        val highlightAfter = readAction { highlightFilter.shouldHighlight(psiFile) }
        assertTrue("Script should be highlighted after the import is over", highlightAfter)
    }


    class Simple : GradleBuildFileHighlightingTest() {
        @TargetVersions("6.0.1+")
        @Test
        fun testSimple() {
            val buildGradleKts = configureByFiles().findBuildGradleKtsFile()
            withHighLightingFilterChecked(buildGradleKts) {
                importProjectUsingSingeModulePerGradleProject()
            }
            checkHighlighting(buildGradleKts)
        }
    }

    class ComplexBuildGradleKts : GradleBuildFileHighlightingTest() {
        @Ignore
        @TargetVersions("4.8+")
        @Test
        fun testComplexBuildGradleKts() {
            val buildGradleKts = configureByFiles().findBuildGradleKtsFile()
            withHighLightingFilterChecked(buildGradleKts) {
                importProjectUsingSingeModulePerGradleProject()
            }
            checkHighlighting(buildGradleKts)
        }

    }

    class JavaLibraryPlugin14 : GradleBuildFileHighlightingTest() {
        @Test
        @TargetVersions("6.0.1+")
        fun testJavaLibraryPlugin() {
            val buildGradleKts = configureByFiles().findBuildGradleKtsFile()
            withHighLightingFilterChecked(buildGradleKts) {
                importProject()
            }

            checkHighlighting(buildGradleKts)
        }

    }

    class MultiplesJdkTableEntriesWithSamePathButFirstHasCorruptedRoots : GradleBuildFileHighlightingTest() {
        @Test
        @TargetVersions("6.0.1+")
        fun testSimple() {
            val jdkTableEntries = ProjectJdkTable.getInstance().allJdks
            assertEquals(2, jdkTableEntries.size)
            assertEquals(1, jdkTableEntries.map { it.homePath }.toSet().size)

            val (corruptedJdk, validJdk) = jdkTableEntries
            assertTrue(corruptedJdk.rootsUrls.isNotEmpty())
            assertTrue(validJdk.rootsUrls.isNotEmpty())
            assertFalse(corruptedJdk.rootsFiles.isNotEmpty())
            assertTrue(validJdk.rootsFiles.isNotEmpty())

            val buildGradleKts = configureByFiles().findBuildGradleKtsFile()
            withHighLightingFilterChecked(buildGradleKts) {
                importProject()
            }
            checkHighlighting(buildGradleKts)
        }

        override fun populateJdkTable(jdks: MutableList<Sdk>) {
            val corruptedJdk = jdks.first().cloneWithCorruptedRoots(myTestDir)
            jdks.add(0, corruptedJdk)
            super.populateJdkTable(jdks)
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