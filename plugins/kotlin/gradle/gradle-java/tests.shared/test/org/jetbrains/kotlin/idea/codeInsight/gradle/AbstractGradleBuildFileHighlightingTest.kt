// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.highlighter.KotlinProblemHighlightFilter
import org.jetbrains.kotlin.idea.highlighter.checkHighlighting
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.parseDirectives
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

abstract class AbstractGradleBuildFileHighlightingTest : KotlinGradleImportingTestCase() {

    // Not to depend on JDK11_HOME variable
    override fun requireJdkHome(): String {
        return requireJdkHome(currentGradleVersion, JavaVersionRestriction.NO)
    }

    fun doTest(filePath: String) {
        // TODO: the value of these tests to be revisited
        if (true) return

        print("Looking for compatible KGP version for $gradleVersion ... ")

        var kgpVersion = GRADLE_TO_KGP_VERSION[gradleVersion]
        println("it's $kgpVersion")

        if (kgpVersion == null) {
            kgpVersion = LATEST_STABLE_GRADLE_PLUGIN_VERSION
            println("Using LATEST_STABLE_GRADLE_PLUGIN_VERSION = $LATEST_STABLE_GRADLE_PLUGIN_VERSION")
        }

        val gradleKtsFiles = configureByFiles(mapOf(
            "kotlin_plugin_version" to kgpVersion.toString()
        ))
            .filter { "kts" == it.extension }
            .also { check(it.isNotEmpty()) { "No .kts files detected for $filePath" } }

        println("Detected .kts files: ${gradleKtsFiles.relativeToProjectRoot()}")
        withHighLightingFilterChecked(gradleKtsFiles.first()) {
            importProject()
        }

        gradleKtsFiles.forEach {
            checkHighlighting(it)
        }
    }

    private fun withHighLightingFilterChecked(file: VirtualFile, block: () -> Unit) = runBlocking {
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


    private fun Path.relativeToProjectRoot(): String =
        relativeTo(myProjectRoot.toNioPath()).pathString

    protected fun VirtualFile.relativeToProjectRoot(): String =
        toNioPath().relativeToProjectRoot()

    private fun List<VirtualFile>.relativeToProjectRoot(): List<String> =
        map { it.relativeToProjectRoot() }

    protected open val outputFileExt = ".highlighting"

    protected open fun checkHighlighting(file: VirtualFile) {
        runInEdtAndWait {
            runReadAction {
                val psiFile = PsiManager.getInstance(myProject).findFile(file) as? KtFile
                    ?: error("Couldn't find psiFile for virtual file: ${file.canonicalPath}")

                val ktsFileUnderTest = File(testDataDirectory(), file.relativeToProjectRoot())
                val ktsFileHighlighting = ktsFileUnderTest.resolveSibling("${ktsFileUnderTest.path}$outputFileExt")
                val directives = parseDirectives(ktsFileUnderTest.readText())

                checkHighlighting(psiFile, ktsFileHighlighting, directives, myProject, highlightWarnings = true)
            }
        }
    }

    override fun testDataDirName() = "highlighting"
}