// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.groovy.inspections.DifferentKotlinGradleVersionInspection
import org.jetbrains.kotlin.idea.inspections.runInspection
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.io.File

class GradleInspectionTest4 : KotlinGradleImportingTestCase() {
    @Test
    fun testDifferentStdlibGradleVersion() {
        val problems = getInspectionResultFromTestDataProject()

        assertEquals(1, problems.size)
        assertEquals("Plugin version (1.3.20) is not the same as library version (1.3.30)", problems.single())
    }

    @Test
    fun testDifferentStdlibGradleVersionWithImplementation() {
        val problems = getInspectionResultFromTestDataProject()

        assertEquals(1, problems.size)
        assertEquals("Plugin version (1.3.20) is not the same as library version (1.3.30)", problems.single())
    }

    @Test
    fun testDifferentStdlibJre7GradleVersion() {
        val problems = getInspectionResultFromTestDataProject()

        assertEquals(1, problems.size)
        assertEquals("Plugin version (1.3.20) is not the same as library version (1.3.30)", problems.single())
    }

    @Test
    fun testDifferentStdlibJdk7GradleVersion() {
        val problems = getInspectionResultFromTestDataProject()

        assertEquals(1, problems.size)
        assertEquals("Plugin version (1.3.20) is not the same as library version (1.3.30)", problems.single())
    }

    @Test
    fun testDifferentStdlibGradleVersionWithVariables() {
        val problems = getInspectionResultFromTestDataProject()

        assertEquals(1, problems.size)
        assertEquals(
            "Plugin version ($LATEST_STABLE_GRADLE_PLUGIN_VERSION) is not the same as library version (1.3.30)",
            problems.single()
        )
    }

    @Test
    fun testDifferentKotlinGradleVersion() {
        val tool = DifferentKotlinGradleVersionInspection()
        tool.testVersionMessage = "\$PLUGIN_VERSION"
        val problems = getInspectionResultFromTestDataProject(tool)

        assertEquals(problems.joinToString(separator = "\n"), "Kotlin version that is used for building with Gradle (1.3.70) is not properly supported in the IDE plugin (\$PLUGIN_VERSION)", problems.single())
    }

    @Test
    @TargetVersions("4.9")
    fun testJreInOldVersion() {
        val problems = getInspectionResultFromTestDataProject()
        assertTrue("The inspection result should be empty but contains the following elements: [$problems].", problems.isEmpty())
    }

    @Test
    @TargetVersions("4.9")
    fun testJreIsDeprecated() {
        val problems = getInspectionResultFromTestDataProject()

        assertEquals(1, problems.size)
        assertEquals(
            "kotlin-stdlib-jre7 is deprecated since 1.2.0 and should be replaced with kotlin-stdlib-jdk7",
            problems.single()
        )
    }

    @Test
    @TargetVersions("4.9")
    fun testJreIsDeprecatedWithImplementation() {
        val problems = getInspectionResultFromTestDataProject()

        assertEquals(1, problems.size)
        assertEquals(
            "kotlin-stdlib-jre7 is deprecated since 1.2.0 and should be replaced with kotlin-stdlib-jdk7",
            problems.single()
        )
    }

    @Test
    @TargetVersions("4.9")
    fun testJreIsDeprecatedWithoutImplicitVersion() {
        val problems = getInspectionResultFromTestDataProject()

        assertEquals(1, problems.size)
        assertEquals(
            "kotlin-stdlib-jre8 is deprecated since 1.2.0 and should be replaced with kotlin-stdlib-jdk8",
            problems.single()
        )
    }

    @Test
    fun testNoDifferentStdlibCommonGradleVersion() {
        val problems = getInspectionResultFromTestDataProject()

        assertTrue("The inspection result should be empty but contains the following elements: [$problems].", problems.isEmpty())
    }

    @Test
    fun testNoDifferentStdlibJdk7GradleVersion() {
        val problems = getInspectionResultFromTestDataProject()

        assertTrue("The inspection result should be empty but contains the following elements: [$problems].", problems.isEmpty())
    }

    @Test
    @TargetVersions("4.9")
    fun testObsoleteCoroutinesUsage() {
        val problems = getInspectionResultFromTestDataProject()

        assertTrue(problems.size == 1)
        assertEquals(
            "Library should be updated to be compatible with Kotlin 1.3",
            problems.single()
        )
    }

    private fun getInspectionResultFromTestDataProject(explicitTool: LocalInspectionTool? = null): List<String> {
        val buildGradle = importProjectFromTestData().find { it.name == "build.gradle" }!!
        val tool = explicitTool ?: run {
            val toolName = File(buildGradle.path).readLines().find { it.startsWith(TOOL) }!!.substring(TOOL.length)
            val inspectionClass = Class.forName(toolName)
            inspectionClass.getDeclaredConstructor().newInstance() as LocalInspectionTool
        }

        return getInspectionResult(tool, buildGradle)
    }

    private fun getInspectionResult(tool: LocalInspectionTool, file: VirtualFile): List<String> {
        val resultRef = Ref<List<String>>()
        runInEdtAndWait {
            runTestRunnable {
                val presentation = runInspection(tool, myProject, listOf(file))

                val foundProblems = presentation.problemElements
                    .values
                    .mapNotNull { it as? ProblemDescriptorBase }
                    .map { it.descriptionTemplate }

                resultRef.set(foundProblems)
            }
        }

        return resultRef.get()
    }

    override fun testDataDirName(): String = "inspections"

    companion object {
        private const val TOOL = "// TOOL: "
    }
}
